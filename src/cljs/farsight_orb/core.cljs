(ns farsight-orb.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defonce app-state (atom {:text ""}))

(defn submit-new-text [data owner]
  (let [new-text (-> (om/get-node owner "new-text") .-value)]
    (when new-text
      (om/update! data [:text] new-text))))

(defn main []
  (om/root
    (fn [cursor owner]
      (reify
        om/IRender
        (render [_]
          (dom/div nil
            (dom/div nil
              (dom/h2 nil "Input")
              (dom/input #js {:type "text" :ref "new-text" :placeholder "Enter some text..."})
              (dom/button #js {:onClick #(submit-new-text cursor owner)} "Submit"))
            (dom/div nil
              (dom/h2 nil "Output")
              (dom/h3 nil (:text cursor)))))))
    app-state
    {:target (. js/document (getElementById "app"))}))
