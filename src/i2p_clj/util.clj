(ns i2p-clj.util
  (:require [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [net.i2p.client I2PClientFactory]))

(defn to-transit [data]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer data)
    out))

(defn from-transit [t]
  (let [in     (ByteArrayInputStream. (if (bytes? t)
                                        t
                                        (.toByteArray t)))
        reader (transit/reader in :json)]
    (transit/read reader)))

(defn create-destination []
  (let [os     (new ByteArrayOutputStream)
        client (I2PClientFactory/createClient)]
    (.toBase64 (.createDestination client os))))
