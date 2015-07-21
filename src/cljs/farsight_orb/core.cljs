(ns farsight-orb.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [farsight-orb.utils :as util]
            [om-bootstrap.grid :as g]))

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
                          :players []
                          :auto-complete-results []
                          :ac-chan (chan)
                          :ac-count 0}))

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

(defn autocomplete-results-loop
  "Handle auto-complete results asynchronously"
  [app owner]
  (let [counter (atom 0)]
    (go (while true
          (let [{:keys [player ac-idx]} (<! (:ac-chan app))]
            (when (not (= @counter ac-idx))
              (reset! counter ac-idx)
              (om/update! app :auto-complete-results []))
            (om/transact! app :auto-complete-results #(conj % player)))))))

(defn update-player-search [value app]
  (let [new-ac-count (inc (:ac-count app))]
    (swap! app-state assoc :ac-count new-ac-count)
    (if (empty? value)
      (om/update! app :auto-complete-results [])
      (let [pattern (re-pattern (clojure.string/lower-case value))]
        (doseq [player (:players app)]
          (if (re-find pattern (clojure.string/lower-case player))
            (put! (:ac-chan app) {:player player :ac-idx new-ac-count})))))))

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
                     (om/update! app :players
                                 res))}))
    om/IWillMount
    (will-mount [this]
      (event-loop app owner)
      (autocomplete-results-loop app owner))
    om/IRenderState
    (render-state [this state]
            (if (not (:connected app))
              (dom/span nil "Loading...")
              (dom/div {:class "container"}
                       (g/grid {}
                               (g/row {:class "player-search-input"}
                                      (dom/h2 "Search")
                                      (dom/input {:type "text"
                                                  :ref "new-text"
                                                  :placeholder "Enter a player name or tag..."
                                                  :style {:width "100%"}
                                                  :onInput #(update-player-search (.. % -target -value) app)}))
                               (g/row {:class "player-search-results"}
                                      (dom/h2 "Results")
                                      (if (not (empty? (:auto-complete-results @app-state)))
                                        (dom/ul
                                         (map
                                          #(dom/li %)
                                          (:auto-complete-results @app-state)))))))))))

(defn main []
  (om/root
    application
    app-state
    {:target (. js/document (getElementById "app"))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
