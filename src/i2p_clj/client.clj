;; I2P Client Functions
;;
;; This namespace provides functions for creating I2P client connections
;; that can connect to remote I2P destinations (servers) anonymously.
;;
;; Key functions:
;; - create-i2p-socket-client: Creates a client that connects to a remote I2P destination
;; - connect-with-retries: Handles connection retry logic with exponential backoff
;; - socket-reader-loop: Manages the async message reading loop for client sockets
;;
;; The client handles the full lifecycle of I2P connections including:
;; - Socket manager creation with destination keys and connection filters
;; - Automatic retry logic for failed connections
;; - Asynchronous message handling with configurable callbacks
;; - Proper resource cleanup and error handling
;;
;; Example usage:
;; (create-i2p-socket-client
;;   {:destination-key "mykey.b32"
;;    :remote-address "server-destination-b64"
;;    :on-receive #(println "Received:" %)
;;    :on-socket-close #(println "Connection closed")})

(ns i2p-clj.client
  (:require
   [i2p-clj.manager :as manager]
   [taoensso.timbre :refer [error info]])
  (:import
   (java.io DataInputStream DataOutputStream)
   (net.i2p.data Destination)))

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

(defn socket-reader-loop
  [session-socket
   input-stream
   data-input-stream
   output-stream
   data-output-stream
   on-receive
   on-socket-close]
  (future
    (try
      (while (not (.isClosed session-socket))
        (on-receive
         {:session-socket session-socket
          :peer-address (-> session-socket
                            .getPeerDestination
                            .toBase64)
          :this-address (-> session-socket
                            .getThisDestination
                            .toBase64)
          :input-stream input-stream
          :data-input-stream data-input-stream
          :output-stream output-stream
          :data-output-stream data-output-stream}))

      ;; TODO Handle exceptions
      (catch java.io.EOFException e
        (info (str "server java.io.EOFException with message: "
                   (.getMessage e))))
      (catch java.io.IOException e
        (info "server java.io.IOException with message: " (.getMessage e)))
      (catch Exception e
        (do (on-socket-close)
            (error "Exception in i2p server handler read loop " e))))))

(defn create-i2p-socket-client
  [{destination-key :destination-key
    remote-address :remote-address
    connection-filter :connection-filter
    client-options :client-options
    retry-attempts :retry-attempts
    on-receive :on-receive
    on-socket-close :on-socket-close

    ;; default connection-filter is to allow all
    :or {client-options (->> (System/getProperties)
                             .clone
                             (cast java.util.Properties))
         retry-attempts 5
         on-receive #(info
                      (str "client receives message with noop. "
                           "Configure on-receive"))
         on-socket-close #(info "socket closing")}}]

  ;; TODO: check for valid destination key and remote address.
  ;; by checking for minimum string length, valid characters for base32/64
  (when-not (string? destination-key)
    (throw (new IllegalArgumentException
                (str "Invalid destination-key argument: "
                     destination-key "."
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
                     "Expected a number for connection retry-attempts "
                     "client to connect to."))))

  (let [manager (manager/create-socket-manager
                 :destination-key destination-key
                 :connection-filter connection-filter
                 :options client-options)
        session (.getSession manager)
        socket (connect-with-retries manager
                                     remote-address
                                     retry-attempts)
        input-stream (.getInputStream socket)
        data-input-stream (new DataInputStream input-stream)
        output-stream (.getOutputStream socket)
        data-output-stream (new DataOutputStream output-stream)]

    (socket-reader-loop socket
                        input-stream
                        data-input-stream
                        output-stream
                        data-output-stream
                        on-receive
                        on-socket-close)

    {:manager manager
     :socket socket
     :input-stream input-stream
     :data-input-stream data-input-stream
     :output-stream output-stream
     :data-output-stream data-output-stream
     :session session}))
