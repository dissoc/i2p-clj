(ns i2p-clj.util
  (:require [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [net.i2p.client I2PClientFactory]))

(defn to-transit [data]
  (with-open [out (ByteArrayOutputStream. 4096)]
    (let [writer (transit/writer out :json)]
      (transit/write writer data)
      out)))

(defn from-transit [t]
  (with-open [in (ByteArrayInputStream. (if (bytes? t)
                                          t
                                          (.toByteArray t)))]
    (let [reader (transit/reader in :json)]
      (transit/read reader))))

(defn create-destination []
  (with-open [os (new ByteArrayOutputStream)]
    (let [client (I2PClientFactory/createClient)
          dest   (.createDestination client os)]
      {:priv-key-b32 (-> os .toByteArray net.i2p.data.Base32/encode)
       :b64          (.toBase64 dest)
       :b32          (.toBase32 dest)
       :cert         (.getCertificate dest)
       :enc-type     (.getEncType dest)
       :pub-key      (.getPublicKey dest)
       :hash         {:b32 (-> dest .getHash .toBase32)}})))
