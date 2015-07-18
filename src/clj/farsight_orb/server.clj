(ns farsight-orb.server
  (:require
           [clojure.core.async :as async :refer [<! <!! chan go thread]]
            [clojure.java.io :as io]
            [clojure.core.cache :as cache]
            [farsight-orb.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [net.cgrand.reload :refer [auto-reload]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.session :as session]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [farsight-orb.data :as data]
            [farsight-orb.events :as events]))


;;
(def static-data (atom {:players []}))

;;
(defn generate-uid [& _]
  (rand-int 10000))


;;;;;;;;;;:
;; Sente ;;
;;;;;;;;;;:
(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket!
         sente-web-server-adapter {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

;;;;;;;;;;:

(deftemplate page (io/resource "index.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(defn index
  "Handle index page request. Injects session uid if needed."
  [req]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :session (if (events/session-uid req)
              (:session req)
              (assoc (:session req) :uid (generate-uid)))
   ;;:body (slurp "index.html")})
   :body (page)})

(defn get-static-data
  "Gets data from the static atom"
  [key req]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :session (if (events/session-uid req)
              (:session req)
              (assoc (:session req) :uid (generate-uid)))
   :body (pr-str (get @static-data key))})


(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})

  ;;;;;;;;;;:
  ;; Sente ;;
  ;;;;;;;;;;:
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  ;;;;;;;;;;:

  (GET  "/data/players" req (#'get-static-data :players req))
  (GET  "/"             req (#'index req)))

(def http-handler
  (->
   (if is-dev?
    (reload/wrap-reload (wrap-defaults #'routes api-defaults))
    (wrap-defaults routes api-defaults))
   ;; Sente
   ring.middleware.keyword-params/wrap-keyword-params
   ring.middleware.params/wrap-params
   session/wrap-session))

(defn run-web-server [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (print "Starting web server on port" port ".\n")
    (run-server http-handler {:port port :join? false})))

(defn run-auto-reload [& [port]]
  (auto-reload *ns*)
  (start-figwheel))

(defn event-loop
  "Handle inbound Sente events."
  []
  (go (loop [{:keys [client-uuid ring-req event] :as data} (<! ch-chsk)]
       ;; (println "-" event)
        (thread (events/handle-event chsk-send! event ring-req))
        (recur (<! ch-chsk)))))

(defn update-static-data
  "Updates the static data"
  []
  (reset! static-data
          {:players data/player-list}))

(defn run [& [port]]

  ;; sente events
  (event-loop)
  (println "Sente event loop is running.")

  ;; static data
  (update-static-data)
  (println "Static data was updated.")

  (when is-dev?
    (run-auto-reload))
  (run-web-server port))

(defn -main [& [port]]
  (run port))
