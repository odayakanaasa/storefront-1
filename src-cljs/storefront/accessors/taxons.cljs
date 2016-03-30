(ns storefront.accessors.taxons
  (:require [clojure.string :as string]
            [storefront.utils.query :as query]
            [storefront.keypaths :as keypaths]))

(def filter-nav-taxons
  (partial filter (complement :stylist_only?)))

(def filter-stylist-taxons
  (partial filter :stylist_only?))

(defn taxon-path-for [taxon]
  (:slug taxon))

(defn taxon-name-from [taxon-path]
  (string/replace taxon-path #"-" " "))

(defn default-stylist-taxon-path [app-state]
  (when-let [default-taxon (->> (get-in app-state keypaths/taxons)
                                (filter :stylist_only?)
                                first)]
    (taxon-path-for default-taxon)))

(defn current-taxon [app-state]
  (query/get (get-in app-state keypaths/browse-taxon-query)
             (get-in app-state keypaths/taxons)))
