(ns i2p-clj.util
  (:require [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

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
