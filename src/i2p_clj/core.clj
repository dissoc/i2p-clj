(ns i2p-clj.core
  (:require
   [i2p-clj.util :refer [b32->input-stream from-transit to-transit]]
   [taoensso.timbre :refer [error info warn]])
  (:import
   (java.io DataInputStream DataOutputStream File)
   (java.util Properties)
   (net.i2p.client.naming NamingService)
   (net.i2p.client.streaming I2PSocketManagerFactory IncomingConnectionFilter)
   (net.i2p.data Destination)
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

(defn ^Boolean create-connection-filter
  "Filter returns boolean with input of destination
  Here you check to allow -> true or to deny -> false"
  [check-destination-fn]
  (reify IncomingConnectionFilter
    (allowDestination [this destination]
      (let [address-base64 (.toBase64 destination)]
        (check-destination-fn address-base64)))))

(defn create-router [& {:keys [config]}]
  (let [r (if config
            (new Router config)
            (new Router))]
    (.setKillVMOnEnd r false)
    (.runRouter r)
    (info "Waiting for router to start...")
    (while (not (.isRunning r))
      (Thread/sleep 1000))
    (info "Router started.")
    r))

(defn default-connection-filter
  "the default behavior is to allow all connections"
  [source-address]
  (warn (str "server default connection filter allows "
             "connection from: " source-address))
  true)

(defn connect-with-retries
  "TODO handle different failure reasons"
  [manager remote-address n-times]
  (let [remote-destination (Destination. remote-address)]
    (if (zero? n-times)
      (throw (Exception. "Unable to connect to remote destination"))
      (try
        (info "connecting to remote destination...")
        (.connect manager remote-destination)
        (catch Exception e
          (do
            (info "failed to connect. retrying in 60 seconds...")
            (Thread/sleep (* 60 1000))
            (connect-with-retries manager remote-address (dec n-times))))))))

(defn create-i2p-socket-client
  [{destination-key   :destination-key
    remote-address    :remote-address
    connection-filter :connection-filter
    client-options    :client-options
    retry-attempts    :retry-attempts
    ;; default connection-filter is to allow all
    :or               {connection-filter (fn [_]
                                           (warn "client default connection filter allows connection.")
                                           true)
                       client-options    (->> (System/getProperties)
                                              .clone
                                              (cast java.util.Properties))
                       retry-attempts    5}}]

  ;; TODO: check for valid destination key and remote address.
  ;; by checking for minimum string length, valid characters for base32/64
  (when-not (string? destination-key)
    (throw (new IllegalArgumentException
                (str "Invalid destination-key argument: " destination-key "."
                     "Expected a base32 encoded destination key."))))

  (when-not (string? remote-address)
    (throw (new IllegalArgumentException
                (str "Invalid argument provided for the remote-address: "
                     remote-address
                     "Expected a base64 address of the remote server for the "
                     "client to connect to."))))

  (when-not (number? retry-attempts)
    (throw (new IllegalArgumentException
                (str "Invalid argument provided for retry-attempts "
                     retry-attempts
                     "Expected a number for connection retry-attempts " "client to connect to."))))

  (let [k                  (b32->input-stream destination-key)
        manager            (if connection-filter
                             (I2PSocketManagerFactory/createManager k connection-filter)
                             (I2PSocketManagerFactory/createManager k))
        session            (.getSession manager)
        socket             (connect-with-retries manager
                                                 remote-address
                                                 retry-attempts)
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
  [session-socket
   on-receive
   on-connection-close]
  (future
    (try
      (with-open [input-stream       (.getInputStream session-socket)
                  data-input-stream  (new DataInputStream input-stream)
                  output-stream      (.getOutputStream session-socket)
                  data-output-stream (new DataOutputStream output-stream)]
        (while (not (.isClosed session-socket))
          (on-receive {:session-socket     session-socket
                       :peer-address       (-> session-socket
                                               .getPeerDestination
                                               .toBase64)
                       :server-address     (-> session-socket
                                               .getThisDestination
                                               .toBase64)
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
        (do (on-connection-close session-socket)
            (error "Exception in i2p server handler read loop " e))))))

(defn server-socket-handler
  [server-socket
   on-receive
   on-server-close
   on-connection-close
   run-server?]
  (future
    (info "starting i2p socket server handler...")
    (try
      (while @run-server?
        (let [session-socket (.accept server-socket)]
          (info "socket server accepted new client connection")
          (server-socket-reader-loop session-socket
                                     on-receive
                                     on-connection-close)))
      (on-server-close)
      (catch java.net.ConnectException e
        (info (str "server java.net.ConnectException with message: "
                   (.getMessage e))))
      (catch Exception e
        (error "Exception in i2p handler loop: " e)))))

(defn create-socket-manager
  [key-stream connection-filter]
  (I2PSocketManagerFactory/createManager key-stream
                                         (-> connection-filter
                                             create-connection-filter)))

(defn create-i2p-socket-server
  ":destination-key base32 string encoded destination key. this is created
  when the destination is initially created

  :connection-filter function that has 1 argument: Destination of incoming
  connection. The function returns a boolean, allow (true, of disallow (false))"
  [{destination-key   :destination-key
    connection-filter :connection-filter
    on-receive        :on-receive
    run-server?       :run-server?
    on-server-close   :on-server-close
    on-client-close   :on-client-close

    :or {connection-filter default-connection-filter
         on-receive        #(info
                              (str "server receives message  "
                                   "with noop. Configure on-receive"))
         on-server-close   (info "server socket handler closed")
         on-client-close   (info "client disconnected")
         run-server?       (atom true)}}]
  (when-not (string? destination-key)
    (throw (new IllegalArgumentException
                (str "Invalid destination-key argument: "
                     destination-key "."
                     "Expected a base32 encoded destination key."))))
  (let [key-stream    (b32->input-stream destination-key)
        manager       (create-socket-manager key-stream
                                             connection-filter)
        server-socket (.getServerSocket manager)
        session       (.getSession manager)]

    (server-socket-handler server-socket
                           on-receive
                           on-server-close
                           on-client-close
                           run-server?)

    {:manager       manager
     :server-socket server-socket
     :session       session
     :run-server    run-server?}))
