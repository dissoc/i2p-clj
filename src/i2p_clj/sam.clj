(ns i2p-clj.sam)

(defn samv3 [stream-id destination]
  (str "HELLO VERSION MIN=3.0 MAX=3.0\n"
       "SESSION CREATE STYLE=STREAM ID="
       stream-id
       " DESTINATION="
       destination))
