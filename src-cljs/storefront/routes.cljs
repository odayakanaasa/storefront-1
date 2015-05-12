(ns storefront.routes
  (:require [bidi.bidi :as bidi]
            [storefront.state :as state]
            [storefront.events :as events]
            [cljs.core.async :refer [put!]]
            [cljs.reader :refer [read-string]]
            [goog.events]
            [goog.history.EventType :as EventType]
            [cemerick.url :refer [map->query url]])
  (:import [goog.history Html5History]))

(extend-protocol bidi.bidi/Pattern
  cljs.core.PersistentHashMap
  (match-pattern [this env]
    (when (every? (fn [[k v]]
                    (cond
                     (or (fn? v) (set? v)) (v (get env k))
                     :otherwise (= v (get env k))))
                  (seq this))
      env))
  (unmatch-pattern [_ _] ""))

(extend-protocol bidi.bidi/Matched
  cljs.core.PersistentHashMap
  (resolve-handler [this m] (some #(bidi.bidi/match-pair % m) this))
  (unresolve-handler [this m] (some #(bidi.bidi/unmatch-pair % m) this)))

(defn edn->bidi [value]
  (keyword (prn-str value)))

(defn bidi->edn [value]
  (read-string (name value)))

(defn set-current-page [app-state]
  (let [uri (.getToken (get-in app-state state/history-path))

        {nav-event :handler params :route-params}
        (bidi/match-route (get-in app-state state/routes-path) uri)

        query-params (:query (url js/location.href))
        event-ch (get-in app-state state/event-ch-path)]
    (put! event-ch
          [(bidi->edn nav-event)
           (assoc params :query-params query-params)])))

(defn history-callback [app-state]
  (fn [e]
    (set-current-page @app-state)))

(defn make-history [callback]
  (doto (Html5History.)
    (.setUseFragment false)
    (.setPathPrefix "")
    (.setEnabled true)
    (goog.events/listen EventType/NAVIGATE callback)))

(defn routes []
  ["" {"/" (edn->bidi events/navigate-home)
       ["/categories/hair/" :taxon-path] (edn->bidi events/navigate-category)
       ["/products/" :product-path] (edn->bidi events/navigate-product)
       "/guarantee" (edn->bidi events/navigate-guarantee)
       "/help" (edn->bidi events/navigate-help)
       "/policy/privacy" (edn->bidi events/navigate-privacy)
       "/policy/tos" (edn->bidi events/navigate-tos)
       "/login" (edn->bidi events/navigate-sign-in)
       "/signup" (edn->bidi events/navigate-sign-up)}])

(defn install-routes [app-state]
  (let [history (or (get-in @app-state state/history-path)
                    (make-history (history-callback app-state)))]
    (swap! app-state
           merge
           {:routes (routes)
            :history history})))

(defn append-query-string [s query-params]
  (if (seq query-params)
    (str s "?" (map->query query-params))
    s))

(defn path-for [app-state navigation-event & [args]]
  (let [query-params (:query-params args)
        args (dissoc args :query-params)]
    (-> (apply bidi/path-for
               (get-in app-state state/routes-path)
               (edn->bidi navigation-event)
               (apply concat (seq args)))
        (append-query-string query-params))))

(defn enqueue-navigate [app-state navigation-event & [args]]
  (let [query-params (:query-params args)
        args (dissoc args :query-params)]
    (.setToken (get-in app-state state/history-path)
               (-> (path-for app-state navigation-event args)
                   (append-query-string query-params)))))
