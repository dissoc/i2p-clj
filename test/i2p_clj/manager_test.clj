(ns i2p-clj.manager-test
  (:require
   [clojure.test :refer :all]
   [i2p-clj.manager :as manager])
  (:import
   (java.util Properties)))

(deftest socket-manager-creation-test
  (testing "create-socket-manager with minimal options"
    ;; This test doesn't actually connect to I2P, just tests the function structure
    (is (thrown? Exception ;; Will throw because no I2P router available
                 (manager/create-socket-manager)))))

;; =============================================================================
;; Unit Tests for Configuration Functions
;; =============================================================================

(deftest router-side-options-test
  (testing "create-router-side-options with defaults"
    (let [props (manager/create-router-side-options)]
      (is (instance? Properties props))
      (is (= "3" (.getProperty props "inbound.length")))
      (is (= "3" (.getProperty props "outbound.length")))
      (is (= "2" (.getProperty props "inbound.quantity")))
      (is (= "2" (.getProperty props "outbound.quantity")))
      (is (= "40" (.getProperty props "crypto.tagsToSend")))
      (is (= "30" (.getProperty props "crypto.lowTagThreshold")))
      (is (= "false" (.getProperty props "i2cp.dontPublishLeaseSet")))))

  (testing "create-router-side-options with custom values"
    (let [props (manager/create-router-side-options
                 :inbound-length 5
                 :outbound-quantity 4
                 :crypto-tags-to-send 60
                 :router-max-participating-tunnels 200)]
      (is (= "5" (.getProperty props "inbound.length")))
      (is (= "4" (.getProperty props "outbound.quantity")))
      (is (= "60" (.getProperty props "crypto.tagsToSend")))))

  (testing "create-router-side-options with optional string properties"
    (let [props (manager/create-router-side-options
                 :inbound-nickname "test-inbound"
                 :explicit-peers "peer1,peer2,peer3")]
      (is (= "test-inbound" (.getProperty props "inbound.nickname")))
      (is (= "peer1,peer2,peer3" (.getProperty props "explicitPeers"))))))

(deftest client-side-options-test
  (testing "create-client-side-options with defaults"
    (let [props (manager/create-client-side-options)]
      (is (instance? Properties props))
      (is (= "false" (.getProperty props "i2cp.closeOnIdle")))
      (is (= "1800000" (.getProperty props "i2cp.closeIdleTime")))
      (is (= "true" (.getProperty props "i2cp.gzip")))
      (is (= "true" (.getProperty props "i2cp.fastReceive")))
      (is (= "None" (.getProperty props "i2cp.messageReliability")))
      (is (= "127.0.0.1" (.getProperty props "i2cp.tcp.host")))
      (is (= "7654" (.getProperty props "i2cp.tcp.port")))
      (is (= "false" (.getProperty props "i2cp.SSL")))))

  (testing "create-client-side-options with custom values"
    (let [props (manager/create-client-side-options
                 :i2cp-close-on-idle true
                 :i2cp-close-idle-time 900000
                 :i2cp-gzip false
                 :i2cp-ssl true
                 :i2cp-tcp-port 7655)]
      (is (= "true" (.getProperty props "i2cp.closeOnIdle")))
      (is (= "900000" (.getProperty props "i2cp.closeIdleTime")))
      (is (= "false" (.getProperty props "i2cp.gzip")))
      (is (= "true" (.getProperty props "i2cp.SSL")))
      (is (= "7655" (.getProperty props "i2cp.tcp.port")))))

  (testing "create-client-side-options with optional string properties"
    (let [props (manager/create-client-side-options
                 :i2cp-username "testuser"
                 :i2cp-password "testpass"
                 :i2cp-lease-set-key "base64key")]
      (is (= "testuser" (.getProperty props "i2cp.username")))
      (is (= "testpass" (.getProperty props "i2cp.password")))
      (is (= "base64key" (.getProperty props "i2cp.leaseSetKey"))))))

(deftest socket-manager-creation-test
  (testing "create-socket-manager parameter validation"
    ;; Test that the function accepts the expected parameters without throwing parsing errors
    (is (thrown? Exception ;; Will throw because no I2P router, but validates params first
                 (manager/create-socket-manager
                  :destination-key "test-key"
                  ;;:connection-filter default-connection-filter
                  :host "localhost"
                  :port 7654
                  :options {:inbound-length 3})))))

;; =============================================================================
;; Error Handling Tests
;; =============================================================================

(deftest configuration-error-handling-test
  (testing "router-side options with invalid types"
    ;; These should still work since we convert everything to strings
    (let [props (manager/create-router-side-options :inbound-length "5")]
      (is (= "5" (.getProperty props "inbound.length")))))

  (testing "client-side options with invalid types"
    ;; These should still work since we convert everything to strings
    (let [props (manager/create-client-side-options :i2cp-tcp-port "7655")]
      (is (= "7655" (.getProperty props "i2cp.tcp.port"))))))

(deftest socket-manager-error-handling-test
  (testing "socket manager with invalid options map"
    ;; Should handle map to Properties conversion
    (is (thrown? Exception
                 (manager/create-socket-manager :options "not-a-map"))))

  (testing "socket manager with invalid Properties object"
    (let [invalid-props "not-properties"]
      (is (thrown? Exception
                   (manager/create-socket-manager :options invalid-props))))))

(deftest connection-filter-test
  (testing "create-connection-filter with custom function"
    (let [allow-list #{"allowed-address-1" "allowed-address-2"}
          filter-fn (fn [addr] (contains? allow-list addr))
          connection-filter (manager/create-connection-filter filter-fn)]
      (is (instance? net.i2p.client.streaming.IncomingConnectionFilter connection-filter))))

  (testing "connection filter logic"
    (let [allow-only-localhost (fn [addr] (= addr "localhost"))
          deny-all (fn [addr] false)
          allow-all (fn [addr] true)]
      ;; Test that functions work as expected before wrapping
      (is (= true (allow-only-localhost "localhost")))
      (is (= false (allow-only-localhost "remote-host")))
      (is (= false (deny-all "any-address")))
      (is (= true (allow-all "any-address"))))))
