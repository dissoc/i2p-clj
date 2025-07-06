(ns i2p-clj.core
  (:require
   [i2p-clj.util :refer [b32->input-stream from-transit to-transit]]
   [taoensso.timbre :refer [error info]])
  (:import
   (java.io DataInputStream DataOutputStream File)
   (java.util Properties)
   (net.i2p.client.naming NamingService)
   (net.i2p.client.streaming I2PSocketManagerFactory IncomingConnectionFilter)
   (net.i2p.router Router RouterContext)))

(defn sender
  "dos will be provided by the server or client socket connection
  message can be any transit compatible data structure"
  [^DataOutputStream dos message]
  (let [message-to-transmit (-> message to-transit .toByteArray)
        transmit-size       (count message-to-transmit)]
    (.writeInt dos transmit-size)
    (.write dos  message-to-transmit 0 transmit-size)))

(defn reader
  "dis is provided bt the server or client socket connection"
  [^DataInputStream dis]
  (let [length  (.readInt dis)
        message (byte-array length)]
    (.readFully dis message 0 length)
    (-> message from-transit)))

;; TODO add more options
(defn create-config [router-dir base-port]
  (doto (new java.util.Properties)
    (.setProperty "i2p.dir.base"
                  (.getAbsolutePath (new File router-dir)))
    (.setProperty "i2p.dir.config"
                  (.getAbsolutePath (new File router-dir "config")))
    (.setProperty "i2p.dir.router"
                  (.getAbsolutePath (new File router-dir "router")))
    (.setProperty "i2p.dir.log"
                  (.getAbsolutePath (new File router-dir "log")))
    (.setProperty "i2p.dir.app"
                  (.getAbsolutePath (new File router-dir "app")))
    (.setProperty "i2np.ntcp.port"
                  (str base-port))
    (.setProperty "i2np.udp.port"
                  (str (+ base-port 1)))
    (.setProperty "i2cp.port"
                  (str (+ base-port 2)))
    (.setProperty "router.adminPort"
                  (str (+ base-port 3)))))

(defn create-client-options
  "options can be found at:
  https://geti2p.net/en/docs/protocol/i2cp#options"
  [& {:keys [inbound-length
             outbound-length
             inbound-quantity
             outbound-quantity]
      :or   {inbound-length    3
             outbound-length   3
             inbound-quantity  2
             outbound-quantity 2}}]
  (doto (new java.util.Properties)
    (.setProperty "inbound.length"
                  (str inbound-length))
    (.setProperty "outbound.length"
                  (str outbound-length))
    (.setProperty "inbound.quantity"
                  (str inbound-quantity))
    (.setProperty "outbound.quantity"
                  (str outbound-quantity))))

(defn create-router [& {:keys [config]}]
  (let [r (if config
            (new Router config)
            (new Router))]
    (.setKillVMOnEnd r false)
    (.runRouter r)
    ;; wait until router is ready. takes many seconds
    (while (not (.isRunning r))
      (Thread/sleep 1000))
    r))

(defn create-i2p-socket-server
  [dest-priv-key-b32
   & {:keys [incoming-connection-filter]}]
  (let [k             (b32->input-stream dest-priv-key-b32)
        manager       (if incoming-connection-filter
                        (I2PSocketManagerFactory/createManager k incoming-connection-filter)
                        (I2PSocketManagerFactory/createManager k))
        server-socket (.getServerSocket manager)
        session       (.getSession manager)]
    {:manager       manager
     :server-socket server-socket
     :session       session}))

(defn connect-with-retries
  "TODO handle different failure reasons"
  [manager remote-destination n-times]
  (if (zero? n-times)
    (throw (Exception. "Unable to connect to remote destination"))
    (try
      (info "connecting to remote destination...")
      (.connect manager remote-destination)
      (catch Exception e
        (do
          (info "failed to connect. retrying in 60 seconds...")
          (Thread/sleep (* 60 1000))
          (connect-with-retries manager remote-destination (dec n-times)))))))

(defn create-i2p-socket-client
  [dest-priv-key-b32
   remote-dest
   & {:keys [incoming-connection-filter client-options]
      :or   {client-options (->> (System/getProperties)
                                 .clone
                                 (cast java.util.Properties))}}]
  (let [k                  (b32->input-stream dest-priv-key-b32)
        manager            (if incoming-connection-filter
                             (I2PSocketManagerFactory/createManager k incoming-connection-filter)
                             (I2PSocketManagerFactory/createManager k))
        session            (.getSession manager)
        socket             (connect-with-retries manager remote-dest 5)
        ;; streams
        input-stream       (.getInputStream socket)
        data-input-stream  (new DataInputStream input-stream)
        output-stream      (.getOutputStream socket)
        data-output-stream (new DataOutputStream output-stream)]
    {:manager            manager
     :socket             socket
     :input-stream       input-stream
     :data-input-stream  data-input-stream
     :output-stream      output-stream
     :data-output-stream data-output-stream
     :session            session}))

(defn ^Boolean create-allowlist
  "Filter returns boolean with input of destination
  Here you check to allow -> true or to deny -> false"
  [& {:keys [on-filter]}]
  (reify IncomingConnectionFilter
    (allowDestination [this destination]
      (if on-filter
        (on-filter destination)
        false))))

(defn base32-address->destination [b32-address]
  (let [naming-service (NamingService/createInstance
                        (RouterContext/getCurrentContext))]
    (.lookup naming-service b32-address)))

(defn socket-reader-loop
  [{socket :socket data-input-stream :data-input-stream :as client}
   & {:keys [on-receive on-socket-close]
      :or   {on-receive      (fn [msg] (info "received packets on the socket."))
             on-socket-close #(info "socket closing")}}]
  (future
    (try
      (while (not (.isClosed socket))
        (on-receive client))
      ;; TODO Handle exceptions
      (catch java.io.EOFException e
        (info (str "server java.io.EOFException with message: "
                   (.getMessage e))))
      (catch java.io.IOException e
        (info "server java.io.IOException with message: " (.getMessage e)))
      (catch Exception e
        (do (on-socket-close)
            (error "Exception in i2p server handler read loop " e))))))

;; NOTE: the server differs because each .accept will have a need to create
;; input and output streams
(defn server-socket-reader-loop
  "NOTE it is important that on-receive reads from a stream so it blocks
  awaiting new bytes to arrive"
  [server-socket
   & {:keys [on-receive
             on-socket-close]
      :or   {on-receive      (fn [_]
                               (info "received packets on the server socket") )
             on-socket-close #(info "socket closing")}}]
  (future
    (try
      (with-open [input-stream       (.getInputStream server-socket)
                  data-input-stream  (new DataInputStream input-stream)
                  output-stream      (.getOutputStream server-socket)
                  data-output-stream (new DataOutputStream output-stream)]
        (while (not (.isClosed server-socket))
          (on-receive {:server-socket      server-socket
                       :input-stream       input-stream
                       :data-input-stream  data-input-stream
                       :output-stream      output-stream
                       :data-output-stream data-output-stream})))
      ;; TODO Handle exceptions
      (catch java.net.ConnectException e
        (info (str "client java.net.ConnectException with message: "
                   (.getMessage e))))
      (catch java.io.IOException e
        (info (str "client java.io.IOException with message: "
                   (.getMessage e))))
      (catch Exception e
        (do (on-socket-close)
            (error "Exception in i2p server handler read loop " e))))))

(def run-server-client-handler? (atom true))

(defn server-socket-handler
  [server-socket
   & {:keys [on-receive on-server-close]
      :or   {on-receive
             (fn [_]
               (info "received packets on the server socket "))
             on-server-close (info "server socket handler closed")}}]
  (future
    (info "starting i2p socket server handler...")
    (try
      (while run-server-client-handler?
        (let [s (.accept server-socket)]
          (info "socket server accepted new client connection")
          (server-socket-reader-loop s :on-receive on-receive)))
      (on-server-close)
      (catch java.net.ConnectException e
        (info (str "server java.net.ConnectException with message: "
                   (.getMessage e))))
      (catch Exception e
        (error "Exception in i2p handler loop: " e)))))
