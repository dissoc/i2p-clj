(ns i2p-clj.core-test
  (:require [clojure.test :refer :all]
            [me.raynes.fs :refer [copy-dir]]
            [i2p-clj.config :refer [config]]
            [i2p-clj.util :as util]
            [cloboss.messaging]
            [cloboss.messaging :as messaging]
            [i2p-clj.core :refer :all])
  (:import [net.i2p.router Router RouterLaunch RouterContext]
           [net.i2p.data Destination]
           [java.io File DataInputStream DataOutputStream]))

;; NOTE: IMPORTANT: tests do not necessarily execute in order. be cautious when
;; adding separate deftests that have dependencies.

;; TODO
;; test settings for router and client

(defonce test-router (atom nil))

(defonce test-sender-queue (messaging/queue "test-sender-queue"))

(defonce test-sender-queue-listener
  (messaging/listen test-sender-queue
                    (fn [m] (println m))))

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

      (testing "creating socket server"
        (let [server-socket (create-i2p-socket-server
                             (-> config :server :destination :priv-key-b32)
                             :incoming-connection-filter
                             (create-allowlist
                              :on-filter (fn [destination]
                                           (def last-dest destination)
                                           true)))]
          (def my-server-socket server-socket)
          (is (not (.isDestroyed (:manager server-socket))))


          (testing "correct socket destination"
            (let [active-socket-destination (-> server-socket
                                                :session
                                                .getMyDestination
                                                .toBase64)]
              (is (= (-> config :server :destination :b64)
                     active-socket-destination))))

          (testing "creating socket server handler"
            (let [on-receive     (fn [{:keys [socket
                                              input-stream
                                              data-input-stream
                                              output-stream
                                              data-output-stream]
                                       :as   m}]
                                   (let [msg (reader data-input-stream)]
                                     (sender data-output-stream msg)))
                  server-handler (server-socket-handler
                                  (:server-socket server-socket)
                                  :on-receive
                                  on-receive)]))))))

  (testing "the creation of client socket"
    (Thread/sleep (* 120 1000))
    (let [remote-destination         (-> config
                                         :server
                                         :destination
                                         :b64
                                         Destination.)
          {socket            :socket
           data-input-stream :data-input-stream
           :as               client} (create-i2p-socket-client
                                      (-> config :client
                                          :destination
                                          :priv-key-b32)
                                      remote-destination
                                      :incoming-connection-filter
                                      (create-allowlist
                                       :on-filter (fn [destination]
                                                    (def last-dest destination)
                                                    true)))
          text-echo-promise          (promise)
          _                          (reset! response text-echo-promise)
          on-receive                 (fn [{data-input-stream :data-input-stream}]
                                       (deliver @response (reader data-input-stream)))
          client-socket-handler      (socket-reader-loop client
                                                         :on-receive on-receive)
          client-dos                 (->> socket
                                          .getOutputStream
                                          (new DataOutputStream))]
      (def my-client-dos client-dos)
      (def my-client-socket socket)
      (is (not (.isClosed socket)))

      (testing "sending and receiving simple message"
        (let [simple-string (str "this is a simple string test. "
                                 "the next tests will rely on transit")
              _             (sender client-dos simple-string)]
          (is (= (String. @@response)
                 simple-string)))))))
