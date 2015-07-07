(ns storefront.components.reviews
  (:require [sablono.core :refer-macros [html]]
            [om.core :as om]
            [storefront.events :as events]
            [storefront.messages :refer [send]]
            [storefront.routes :as routes]
            [storefront.keypaths :as keypaths]
            [storefront.query :as query]))

(defn reviews-component [data owner]
  (reify
    om/IDidMount
    (did-mount [_] (send data events/reviews-component-mounted))
    om/IWillUnmount
    (will-unmount [_] (send data events/reviews-component-will-unmount))
    om/IRender
    (render [_]
      (html
       (let [product (query/get (get-in data keypaths/browse-product-query)
                                (vals (get-in data keypaths/products)))
             master-variant (:master product)]
         [:div.product-reviews
          [:div.yotpo.yotpo-main-widget
           {:data-product-id (:id master-variant)
            :data-name (:name master-variant)
            :data-url (apply routes/path-for @data
                             (get-in data keypaths/navigation-message))
            :data-image-url (get-in master-variant [:images 0])
            :data-description (:description master-variant)}]])))))
