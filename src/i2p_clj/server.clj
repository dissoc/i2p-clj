;; I2P Server Functions
;;
;; This namespace provides functions for creating I2P server instances that
;; can accept anonymous connections from I2P clients over the I2P network.
;;
;; Key functions:
;; - create-i2p-socket-server: Creates a server that listens for I2P client connections
;; - server-socket-handler: Manages the main server accept loop for incoming connections
;; - server-socket-reader-loop: Handles async message reading for each client connection
;;
;; The server handles the full lifecycle of I2P server operations including:
;; - Socket manager creation with destination keys and connection filtering
;; - Concurrent handling of multiple client connections
;; - Asynchronous message processing with configurable callbacks
;; - Graceful server shutdown and client disconnection handling
;; - Proper resource cleanup and error handling for each connection
;;
;; Example usage:
;; (create-i2p-socket-server
;;   {:destination-key "myserver.b32"
;;    :connection-filter #(allow-connection? %)
;;    :on-receive #(handle-client-message %)
;;    :on-client-close #(log-client-disconnect %)
;;    :run-server? (atom true)})

(ns i2p-clj.server
  (:require
   [i2p-clj.manager :as manager]
   [taoensso.timbre :refer [error info]])
  (:import
   (java.io DataInputStream DataOutputStream)))

(defn server-socket-reader-loop
  "NOTE it is important that on-receive reads from a stream so it blocks
  awaiting new bytes to arrive"
  [session-socket
   on-receive
   on-connection-close]
  (future
    (try
      (with-open [input-stream (.getInputStream session-socket)
                  data-input-stream (new DataInputStream input-stream)
                  output-stream (.getOutputStream session-socket)
                  data-output-stream (new DataOutputStream output-stream)]
        (while (not (.isClosed session-socket))
          (on-receive {:session-socket session-socket
                       :peer-address (-> session-socket
                                         .getPeerDestination
                                         .toBase64)
                       :server-address (-> session-socket
                                           .getThisDestination
                                           .toBase64)
                       :input-stream input-stream
                       :data-input-stream data-input-stream
                       :output-stream output-stream
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

(defn create-i2p-socket-server
  ":destination-key base32 string encoded destination key. this is created
  when the destination is initially created

  :connection-filter function that has 1 argument: Destination of incoming
  connection. The function returns a boolean, allow (true, of disallow (false))"
  [{destination-key :destination-key
    server-options :server-options
    connection-filter :connection-filter
    on-receive :on-receive
    run-server? :run-server?
    on-server-close :on-server-close
    on-client-close :on-client-close

    :or {on-receive #(info
                       (str "server receives message  "
                            "with noop. Configure on-receive"))
         on-server-close #(info "server socket handler closed")
         on-client-close #(info "client disconnected")
         run-server? (atom true)}}]
  (when-not (string? destination-key)
    (throw (new IllegalArgumentException
                (str "Invalid destination-key argument: "
                     destination-key "."
                     "Expected a base32 encoded destination key."))))
  (let [manager (manager/create-socket-manager
                 :destination-key destination-key
                 :connection-filter connection-filter
                 :options server-options)
        server-socket (.getServerSocket manager)
        session (.getSession manager)]

    (server-socket-handler server-socket
                           on-receive
                           on-server-close
                           on-client-close
                           run-server?)
    {:manager manager
     :server-socket server-socket
     :session session
     :run-server run-server?}))
