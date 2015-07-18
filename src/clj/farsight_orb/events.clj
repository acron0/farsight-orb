(ns farsight-orb.events
(:require [farsight-orb.data :as data]))

(defn session-uid
  "Convenient to extract the UID that Sente needs from the request."
  [req]
  (get-in req [:session :uid]))

(defmulti handle-event
  "Handle events based on the event ID."
  (fn [send-fn [ev-id ev-arg] ring-req] ev-id))

(defmethod handle-event :test/reverse
  [send-fn [_ msg] req]
  (when-let [uid (session-uid req)]
    (send-fn uid [:test/reply (clojure.string/reverse msg)])))

;; When the client pings us, send back the session state:

;;(defmethod handle-event :chsk/ws-ping
;;  [_ req]
;;  ni)
;; Handle unknown events.
;; Note: this includes the Sente implementation events like:
;; - :chsk/uidport-open
;; - :chsk/uidport-close

(defmethod handle-event :default
  [send-fn event req] nil
  (comment (println "Default event handler:" event)))
