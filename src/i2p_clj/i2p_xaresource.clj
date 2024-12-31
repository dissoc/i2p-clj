(ns i2p-clj.i2p-xaresource
  (:require [i2p-clj.util :refer [to-transit from-transit]])
  (:import [javax.transaction.xa XAResource Xid XAException]
           [net.i2p.client.streaming I2PSocketManager I2PServerSocket
            I2PSocketManagerFactory]
           [net.i2p.router RouterContext]
           [java.io ByteArrayInputStream ByteArrayOutputStream])
  (:gen-class
   :name i2p_clj.i2p_xaresource.I2pXAResource
   ;;:constructors {[String] []} ; Takes a single string argument
   :state state
   :init init
   :implements [javax.transaction.xa.XAResource]
   ;; additional methods
   :constructors {[String] []}
   :methods [[getMessage [] String]
             [queueMessage [String] java.lang.Void]
             [getTempState [] clojure.lang.PersistentArrayMap]]))
;; (:gen-class
;;  :name "i2p-clj.I2pXAResource")

(defn -init
  [destination]
  [[] (atom {:timeout           10000
             :destination       destination
             :transaction-state {}
             :connection        {}
             :pending-message   {}})]) ; Return a vector of fields for the class

(defn set-state
  [this key value]
  (swap! (.state this) into {key value}))

(defn get-state
  [this & key]
  (if key
    (@(.state this) key)
    @(.state this)))

(defn create-conn [this state]
  (try
    (let [dest    (get-state this :destination)
          manager (I2PSocketManagerFactory/createManager)
          ;;session (.getSession manager)
          socket  (.connect manager dest)
          out     (.getOutputStream socket)
          in      (.getInputStream socket)]
      (swap! (.state this) #(assoc % [:conn] {:dest    dest
                                              :manager manager
                                              :socket  socket
                                              :out     out
                                              :in      in})))))

(defn -getMessage
  [this]
  (.toString this)) ; Access the stored field

(defn -getTempState
  [this]
  @(.state this))

(defn -start [this Xid xid ^Integer flags]
  ;; Handle the flags (TMNOFLAGS, TMJOIN, or TMRESUME) appropriately:
  ;; TMNOFLAGS: Start a new branch. Ensure no other transaction is
  ;; active on the resource.
  ;; TMJOIN: Join an existing transaction branch. Verify that the Xid
  ;; exists and is compatible with the current context.
  ;; TMRESUME: Resume a suspended transaction branch. Restore the previous
  ;; context associated with the Xid.
  (println "start")
  (println flags)
  (if (and (not= flags XAResource/TMNOFLAGS)
           (not= flags XAResource/TMJOIN)
           (not= flags XAResource/TMRESUME))
    (throw (new XAException XAException/XAER_INVAL))
    (if (and (not= flags XAResource/TMNOFLAGS)
             (contains? (:txn-states (get-state)) xid))
      (throw (new XAException XAException/XAER_DUPID))
      (swap! (.state this) #(assoc-in % [:txn-states xid] true)))))

(defn -end [this xid flags]
  (println "end")
  (if (not (contains? (-> @(.state this) :txn-states) xid))
    (throw (new XAException XAException/XAER_NOTA))
    (swap! (.state this) #(assoc-in % [:txn-states xid] false))))

(defn ^Integer -getTransactionTimeout [this]
  (println "gettransactiontimeout")
  (get-state :timeout))

;; implemented but not tested
(defn -commit [this ^Xid xid ^Boolean one-phase]
  (println "commit: ")
  (println one-phase)

  ;; connect
  (try (let [{dest    :dest
              manager :manager
              socket  :socket
              out     :out
              in      :in} (create-conn)]

         ;; store connection in class state
         (swap! (.state this) #(assoc % :connection {:manager manager
                                                     :socket  socket
                                                     :out     out
                                                     :in      in}))

         ;; write event commit
         (.write out (-> {:e :commit}
                         to-transit
                         .toByteArray))

         ;; write message
         (.write out (-> {:e :message
                          :d (get-state :pending-message)}
                         to-transit
                         .toByteArray))

         ;; flush buffer
         (.flush out)

         ;; read response
         (let [response (byte-array 20)
               read     (.read in response)]
           (when (not= read 20)
             (throw (new XAException XAException/XA_RBOTHER)))

           (swap! (.state this) #(update % :pending-message dissoc xid))
           (swap! (.state this) #(assoc-in % [:transaction-state xid] :committed))

           ;; check ack
           (try (let [e (:e (from-transit response))]
                  (when (not= e :ack)
                    (new XAException XAException/XA_RBOTHER)))
                (catch Exception e
                  (throw (new XAException XAException/XA_RBOTHER))))))

       (catch java.io.IOException e
         (throw (new XAException XAException/XAER_RMERR)))))

(defn ^Integer -prepare [this ^Xid xid]
  (println "prepare")
  ;; TODO check connection
  (let [{out :out in :in} (get-state this :connection)]
    (try
      ;; write event prepare
      (.write out (-> {:e :prepare}
                      to-transit
                      .toByteArray))
      (.flush out)

      ;; wait for prepared response
      (let [response (byte-array 25)
            read     (.read in response)]
        (when (not= read 25)
          (throw (new XAException XAException/XA_RBOTHER)))
        ;; check ack
        (try (let [e (:e (from-transit response))]
               (if (= e :prepared)
                 (do
                   (swap! (.state this) #(assoc-in % [:transaction-state xid] :prepared))
                   XAResource/XA_OK)
                 (new XAException XAException/XA_RBOTHER)))
             (catch Exception e
               (throw (new XAException XAException/XA_RBOTHER)))))
      (catch java.io.IOException e
        (throw (new XAException XAException/XAER_RMERR))))))

(defn -queueMessage [this ^Xid xid ^String message]
  (println "queuing message" xid message)
  (if (not (contains? (-> (get-state :txn-states)) xid))
    (throw (new XAException XAException/XAER_NOTA))
    (swap! (.state this) #(assoc-in % [:pending-message xid] message))))


;; settransactiontimeout
;; start
;; 0
;; end
;; commit:
;; true
;; enlistedn?  true
