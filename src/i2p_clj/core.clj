;;; Copyright © 2025 Justin Bishop
;;; (apply str (reverse '("me" "." "dissoc" "@" "mail")))

(ns i2p-clj.core
  (:require
   [i2p-clj.util :refer [from-transit to-transit]])
  (:import
   (java.io DataInputStream DataOutputStream)))

(defn sender
  "dos will be provided by the server or client socket connection
  message can be any transit compatible data structure"
  [^DataOutputStream dos message]
  (let [message-to-transmit (-> message to-transit .toByteArray)
        transmit-size (count message-to-transmit)]
    (.writeInt dos transmit-size)
    (.write dos message-to-transmit 0 transmit-size)))

(defn reader
  "dis is provided bt the server or client socket connection"
  [^DataInputStream dis]
  (let [length (.readInt dis)
        message (byte-array length)]
    (.readFully dis message 0 length)
    (-> message from-transit)))
