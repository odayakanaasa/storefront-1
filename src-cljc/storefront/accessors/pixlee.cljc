(ns storefront.accessors.pixlee
  (:require [cemerick.url :as url]
            [clojure.set :as set]
            [clojure.string :as string]
            [storefront.events :as events]
            [storefront.routes :as routes]))

(def named-search-slug->sku
  {"straight"   "NSH"
   "loose-wave" "LWH"
   "body-wave"  "BWH"
   "deep-wave"  "DWH"
   "curly"      "CUR"
   "closures"   "CLO"
   "frontals"   "FRO"})

(def sku->named-search-slug (clojure.set/map-invert named-search-slug->sku))

(defn sku [{:keys [slug]}]
  (named-search-slug->sku slug))

(defn content-available? [named-search]
  (boolean (sku named-search)))

(defn normalize-user-name [user-name]
  (if (= (first user-name) \@)
    (apply str (rest user-name))
    user-name))

(defn- extract-img-urls [coll original large medium small]
  (-> coll
      (select-keys [original large medium small])
      (set/rename-keys {original :original
                        large    :large
                        medium   :medium
                        small    :small})
      (->>
       (remove (comp string/blank? val))
       (into {}))))

(defn- extract-images
  [{:keys [id user_name content_type source products title source_url pixlee_cdn_photos] :as item}]
  (reduce-kv (fn [result name url] (assoc result name {:src url :alt title}))
             {}
             (merge
              (extract-img-urls item :source_url :big_url :medium_url :thumbnail_url)
              (extract-img-urls pixlee_cdn_photos :original_url :large_url :medium_url :small_url))))

(defn- product-link
  [product]
  (-> product
      :link
      url/url-decode
      url/url
      :path
      routes/navigation-message-for))

(defn parse-ugc-album [album]
  (map (fn [{:keys [id user_name content_type source products title source_url pixlee_cdn_photos] :as item}]
         (let [[nav-event nav-args :as nav-message] (product-link (first products))]
           {:id             id
            :content-type   content_type
            :source-url     source_url
            :user-handle    (normalize-user-name user_name)
            :imgs           (extract-images item)
            :social-service source
            :shared-cart-id (:shared-cart-id nav-args)
            :links          (merge {:view-other nav-message}
                                   (when (= nav-event events/navigate-shared-cart)
                                     ;; TODO: if the view-look? experiment wins, we will not need the purchase-look-link
                                     ;; both navigate-shared-cart and control-create-order-from-shared-cart have
                                     ;; :shared-cart-id in the nav-message
                                     {:view-look     [events/navigate-shop-by-look-details {:look-id id}]
                                      :purchase-look [events/control-create-order-from-shared-cart (assoc nav-args :selected-look-id id)]}))
            :title          title}))
       album))
