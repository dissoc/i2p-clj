(ns i2p-clj.core-test
  (:require
   [clojure.test :refer :all]
   [i2p-clj.core :refer :all]
   [i2p-clj.util :as util]
   [taoensso.timbre :refer [info]])
  (:import
   (java.io DataInputStream DataOutputStream)))

;; NOTE: IMPORTANT: tests do not necessarily execute in order. be cautious when
;; adding separate deftests that have dependencies.

;; TODO test the use of cloboss messaging

(defonce test-router (atom nil))

(defonce destinations (atom {:server nil
                             :client nil}))

(def response (atom nil))

;; ;; check if router directory exists
;; (if (not (.exists router-dir))
;;   (.mkdirs router-dir))
;; (copy-dir "certificates" (str (.getAbsolutePath router-dir)))

(deftest transit-sender-and-reader
  (testing "testing the encoding and decoding of messages via transit"
    ;; this test emulates what would be sent and received over the socket
    (let [baos                    (new java.io.ByteArrayOutputStream)
          dos                     (new DataOutputStream baos)
          sender-data-structure   {:a [1 2 3]
                                   :b '("a" "b" "c")}
          _                       (sender dos sender-data-structure)
          ;; at this point the data would be what would be sent over
          ;; the socket
          ba-from-sender          (.toByteArray baos)
          bais                    (new java.io.ByteArrayInputStream ba-from-sender)
          dis                     (new DataInputStream bais)
          receiver-data-structure (reader dis)]

      (is (= sender-data-structure receiver-data-structure)))))

;; TODO add router config tests
;; TODO test filters
(deftest test-server-and-client-socket
  (testing "the creation of router"
    (let [router-config (doto (new java.util.Properties)
                          (.setProperty "i2cp.enable" "false"))
          router        (if @test-router
                          @test-router
                          (reset! test-router
                                  (create-router
                                   :config router-config)))]
      (is (.isAlive router))

      (testing "creating server and client destination"
        (let [server-destination (util/create-destination)
              client-destination (util/create-destination)]
          (reset! destinations {:server server-destination
                                :client client-destination})
          ;; TODO use spec to validate structure
          (is (and server-destination client-destination))))

      (testing "creating socket server"
        (let [server-key-stream (-> @destinations
                                    :server
                                    :key-stream-base-32)
              allow-set         (conj #{} (-> @destinations
                                              :client
                                              :address-base-64))
              allow-list        (fn [address-base64]
                                  (let [allowed? (contains? allow-set
                                                            address-base64)]
                                    (info (str "address attempting to connect: "
                                               address-base64
                                               " allowed? " allowed?))

                                    allowed?))

              on-receive (fn [{:keys [;; server-socket can be used to
                                      ;; destroy session, respond
                                      ;; based on peer address etc
                                      server-socket
                                      input-stream
                                      data-input-stream
                                      output-stream
                                      data-output-stream]
                               :as   m}]
                           (let [msg (reader data-input-stream)]
                             (info "server received message: "
                                   msg
                                   " from client")
                             (sender data-output-stream msg)))

              server-session (create-i2p-socket-server
                              {:destination-key   server-key-stream
                               :connection-filter allow-list
                               :on-receive        on-receive})]))))

  (testing "the creation of client socket"
    (let [remote-address        (-> @destinations
                                    :server
                                    :address-base-64)
          client-dest-key       (-> @destinations
                                    :client
                                    :key-stream-base-32)
          client                (create-i2p-socket-client
                                 {:destination-key   client-dest-key
                                  :remote-address    remote-address
                                  :connection-filter (create-connection-filter
                                                      (fn [_] true))})
          socket                (:socket client)
          data-input-stream     (:data-input-stream client)
          text-echo-promise     (promise)
          _                     (reset! response text-echo-promise)
          on-receive            (fn [{data-input-stream :data-input-stream}]
                                  (deliver @response (reader data-input-stream)))
          client-socket-handler (socket-reader-loop client
                                                    :on-receive on-receive)
          client-dos            (->> socket
                                     .getOutputStream
                                     (new DataOutputStream))]
      (def my-client-dos client-dos)
      (def my-client-socket socket)
      (is (not (.isClosed socket)))

      (testing "sending and receiving simple message"
        (let [simple-string (str "this is a simple string test. "
                                 "the next tests will rely on transit")]
          (info (str "client sending messge: "
                     simple-string
                     " to server"))
          (sender client-dos simple-string)
          (is (= (String. @@response)
                 simple-string))))))
  (testing "client with address not in allowlist"
    (let [remote-address    (-> @destinations
                                :server
                                :address-base-64)
          dest-key          (:key-stream-base-32 (util/create-destination))
          allow-list        (create-connection-filter
                             (fn [address-base64]
                               true))
          exception-message (try
                              (create-i2p-socket-client
                               {:destination-key   dest-key
                                :remote-address    remote-address
                                :connection-filter allow-list
                                :retry-attempts    1})
                              (catch Exception e (.getMessage e)))]
      (is (= "Unable to connect to remote destination"
             exception-message)))))
