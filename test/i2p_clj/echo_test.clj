(ns i2p-clj.echo-test
  (:require
   [clojure.test :refer :all]
   [i2p-clj.client :as client]
   [i2p-clj.core :refer :all]
   [i2p-clj.router :as r]
   [i2p-clj.server :as server]
   [i2p-clj.util :as util]
   [taoensso.timbre :refer [info]])
  (:import
   (java.io DataOutputStream)
   (java.util Properties)))

;; Example of server and client communication

;; TODO test the use of cloboss messaging

(defonce test-router (atom nil))

(defonce destinations (atom {:server nil
                             :client nil}))

(def response (atom nil))

;; TODO add router config tests
;; TODO test filters
(deftest test-server-and-client-socket
  (testing "the creation of router"
    (let [router-config (doto (new java.util.Properties)
                          (.setProperty "i2cp.enable" "false"))
          router        (if @test-router
                          @test-router
                          (reset! test-router
                                  (r/create-router
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

              server-session (server/create-i2p-socket-server
                              {:destination-key   server-key-stream
                               :connection-filter allow-list
                               :on-receive        on-receive})]))))

  (testing "the creation of client socket"
    (let [remote-address    (-> @destinations
                                :server
                                :address-base-64)
          client-dest-key   (-> @destinations
                                :client
                                :key-stream-base-32)
          text-echo-promise (promise)
          _                 (reset! response text-echo-promise)
          on-receive        (fn [{data-input-stream :data-input-stream}]
                              (deliver @response (reader data-input-stream)))
          client            (client/create-i2p-socket-client
                             {:destination-key   client-dest-key
                              :remote-address    remote-address
                              :connection-filter (fn [_] true)
                              :on-receive        on-receive})
          socket            (:socket client)

          data-input-stream (:data-input-stream client)
          client-dos        (->> socket
                                 .getOutputStream
                                 (new DataOutputStream))]
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
          exception-message (try
                              (client/create-i2p-socket-client
                               {:destination-key dest-key
                                :remote-address  remote-address
                                ;;:connection-filter allow-list
                                :retry-attempts  1})
                              (catch Exception e (.getMessage e)))]
      (is (= "Unable to connect to remote destination"
             exception-message))))
  (testing "shutdown router"
    (let [router @test-router]
      (r/shutdown-router router)
      (is (not (.isAlive router))))))
