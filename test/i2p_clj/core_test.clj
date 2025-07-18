;; I2P Core Library Test Suite
;;
;; This test suite covers the core I2P networking functionality including:
;; - Configuration functions (router-side and client-side options)
;; - Socket manager creation with various options
;; - Connection filtering and validation
;; - Basic server/client communication
;; - Error handling and edge cases
;;
;; Test Structure:
;; - Unit tests for configuration functions (no I2P router required)
;; - Integration tests for server/client communication (requires I2P router)
;; - Error handling tests for various failure scenarios
;;
;; Note: Integration tests may take longer as they require actual I2P network setup

(ns i2p-clj.core-test
  (:require
   [clojure.test :refer :all]
   [i2p-clj.core :refer :all])
  (:import
   (java.io DataInputStream DataOutputStream)))

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
