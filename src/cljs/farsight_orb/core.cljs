(ns farsight-orb.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [farsight-orb.utils :as util]
            [om-bootstrap.button :as b]))

(enable-console-print!)

;;;;;;;;;;:
;; Sente ;;
;;;;;;;;;;:
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]
  (defonce chsk       chsk)
  (defonce ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (defonce chsk-send! send-fn) ; ChannelSocket's send API fn
  (defonce chsk-state state)   ; Watchable, read-only atom
  )
;;;;;;;;;;:

(defonce app-state (atom {:text ""
                          :events []
                          :connected false
                          :players []}))

(defn submit-new-text [data owner]
  (let [new-text (-> (om/get-node owner "new-text") .-value)]
    (when new-text
      (chsk-send! [:test/reverse new-text])
      (om/update! data [:text] new-text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti handle-event
  "Handle events based on the event ID."
  (fn [[ev-id ev-arg] app owner] ev-id))

(defmethod handle-event :test/reply
  [[_ msg] app owner]
  (om/transact! app :events #(conj % msg)))

;; Ignore unknown events (we just print to the console):

(defmethod handle-event :default
  [event app owner]
  (println "UNKNOWN EVENT" event))

;;(chsk-send! [:test/echo (:text state)])

(defn event-loop
  "Handle inbound events."
  [app owner]
  (go (loop [[op arg] (:event (<! ch-chsk))]
        #_(println "-" op "=>" arg)
        (case op
          :chsk/recv (handle-event arg app owner)
          :chsk/handshake (om/update! app :connected true)
          ;; we ignore other Sente events
          nil)
        (recur (:event (<! ch-chsk))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn application
  "Component that represents our application. Maintains session state.
  Selects views based on session state."
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
                (util/edn-xhr
                 {:method :get
                  :url (str "data/players")
                  :on-complete
                   (fn [res]
                     (om/update! app :players res))}))
    om/IWillMount
    (will-mount [this]
                (event-loop app owner))
    om/IRenderState
    (render-state [this state]
            ( if (not (:connected app))
              (dom/span nil "Loading...")
            (dom/div nil
                     (dom/div nil
                              (dom/h2 nil "Input")
                              (dom/input #js {:type "text" :ref "new-text" :placeholder "Enter some text..."})
                              (b/button {:bs-style "primary" :on-click #(submit-new-text app owner)} "Submit"))
                     (dom/div nil
                              (dom/h2 nil "App-state Modification")
                              (dom/span nil (:text app))
                              (dom/h2 nil "WS responses")
                              (apply dom/ul nil (map #(dom/li nil %) (:events app)))))))))

(defn main []
  (om/root
    application
    app-state
    {:target (. js/document (getElementById "app"))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
