(ns storefront.effects
  (:require [storefront.platform.messages :as messages]
            [storefront.events :as events]))

(defmulti perform-effects identity)

(defmethod perform-effects :default [dispatch event args old-app-state app-state])

(defn redirect
  ([event]
   (redirect event nil))
  ([event args]
   (messages/handle-message events/redirect {:nav-message [event args]})))

(defn page-not-found
  []
  (redirect events/navigate-home)
  (messages/handle-message events/flash-later-show-failure {:message "Page not found"}))
