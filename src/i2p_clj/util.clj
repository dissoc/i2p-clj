(ns i2p-clj.util
  (:require
   [cognitect.transit :as transit])
  (:import
   (java.io ByteArrayInputStream ByteArrayOutputStream)
   (net.i2p.client I2PClientFactory)
   (net.i2p.crypto KeyGenerator SigType)
   (net.i2p.data Base32)))


(def default-i2p-sig SigType/EdDSA_SHA512_Ed25519)

(defn to-transit [data]
  (with-open [out (ByteArrayOutputStream. 1024)]
    (let [writer (transit/writer out :json)]
      (transit/write writer data)
      out)))

(defn from-transit [t]
  (with-open [in (ByteArrayInputStream. (if (bytes? t)
                                          t
                                          (.toByteArray t)))]
    (let [reader (transit/reader in :json)]
      (transit/read reader))))

(defn create-destination
  "destination-key-stream contains the destination, PrivateKey,
  and SigningPrivateKey. It can be used to restore the destination
  TODO: handle different sig-types"
  []
  (with-open [os (new ByteArrayOutputStream)]
    (let [client (I2PClientFactory/createClient)
          dest   (.createDestination client os default-i2p-sig)]
      {:key-stream-base-32 (-> os .toByteArray Base32/encode)
       :address-base-64                (.toBase64 dest)
       :address-base-32                (.toBase32 dest)
       :certificate                    (.getCertificate dest)
       :enc-type                       (.getEncType dest)
       :public-key                     (.getPublicKey dest)})))

(defn b32->input-stream [b32-str]
  (-> b32-str
      Base32/decode
      ByteArrayInputStream.))

(defn generate-signing-keys
  "for use with sessions"
  []
  (let [[public-key
         private-key] (-> (KeyGenerator/getInstance)
                          (.generateSigningKeys default-i2p-sig))]
    public-key
    {:public-key  {:bytes    (.getData public-key)
                   :base-32  (-> public-key
                                 .getData
                                 Base32/encode)
                   :sig-type (.getType public-key)
                   :object   public-key}
     :private-key {:bytes    (.getData private-key)
                   :base-32  (-> private-key
                                 .getData
                                 Base32/encode)
                   :sig-type (.getType private-key)
                   :object   private-key}}))
