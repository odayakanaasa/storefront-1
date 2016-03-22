(ns storefront.components.slideout-nav
  (:require [storefront.components.utils :as utils]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [storefront.events :as events]
            [storefront.keypaths :as keypaths]
            [storefront.routes :as routes]
            [storefront.accessors.taxons :refer [taxon-path-for default-stylist-taxon-path]]
            [storefront.accessors.stylists :refer [own-store?]]
            [storefront.accessors.navigation :as navigation]
            [storefront.messages :refer [send]]
            [storefront.components.formatters :refer [as-money]]))

(defn show-store-credit? [app-state]
  (when-let [credit (get-in app-state keypaths/user-total-available-store-credit)]
    (pos? credit)))

(defn close-all-menus [app-state]
  (send app-state
        events/control-menu-collapse
        {:keypath keypaths/menu-expanded})
  (send app-state
        events/control-menu-collapse
        {:keypath keypaths/account-menu-expanded})
  (send app-state
        events/control-menu-collapse
        {:keypath keypaths/shop-menu-expanded}))

(defn close-and-route [app-state event & [args]]
  {:href
   (routes/path-for @app-state event args)
   :on-click
   (fn [e]
     (.preventDefault e)
     (close-all-menus app-state)
     (routes/enqueue-navigate @app-state event args))})

(defn close-and-enqueue [app-state event & [args]]
  {:href "#"
   :on-click
   (fn [e]
     (.preventDefault e)
     (close-all-menus app-state)
     (send app-state event args))})

(defn slideout-nav-link [data {:keys [href on-click icon-class label full-width?]}]
  [:a.slideout-nav-link
   (merge
    {:href href :class (if full-width? "full-width" "half-width")}
    (when on-click {:on-click on-click}))
   [:div.slideout-nav-link-inner
    [:div.slideout-nav-link-icon {:class (str "icon-" icon-class)}]
    label]])

(defn logged-in? [data]
  (boolean (get-in data keypaths/user-email)))

(defn shop-now-attrs [data]
  (apply close-and-route data (navigation/shop-now-navigation-message data)))

(defn slideout-nav-component [data owner]
  (om/component
   (html
    [:div.slideout-nav-wrapper
     {:class (when (get-in data keypaths/menu-expanded)
               "slideout-nav-open")}
     (let [store (get-in data keypaths/store)
           store-slug (get-in data keypaths/store-slug)]
       [:nav.slideout-nav (when-not (store :profile_picture_url)
                            {:class "no-picture"})
        [:div.slideout-nav-header
         [:div.slideout-nav-img-container
          (if-let [profile-picture-url (store :profile_picture_url)]
            [:img.slideout-nav-portrait {:src profile-picture-url}]
            [:div.slideout-nav-portrait.missing-picture])]
         [:h2.slideout-nav-title (store :store_name)]]
        [:div.horizontal-nav-list
         [:div.account-detail
          (if (logged-in? data)
            [:a.account-menu-link
             {:href "#"
              :on-click
              (if (get-in data keypaths/account-menu-expanded)
                (utils/send-event-callback data
                                           events/control-menu-collapse
                                           {:keypath keypaths/account-menu-expanded})
                (utils/send-event-callback data
                                           events/control-menu-expand
                                           {:keypath keypaths/account-menu-expanded}))}
             (when (show-store-credit? data)
               [:span.stylist-user-label "Store credit:"
                [:span.store-credit-amount
                 (as-money (get-in data keypaths/user-total-available-store-credit))]])
             [:span.account-detail-name
              (when (own-store? data)
                [:span.stylist-user-label "Stylist:"])
              (get-in data keypaths/user-email)]
             [:figure.down-arrow]]
            [:span
             [:a (close-and-route data events/navigate-sign-in) "Sign In"]
             " | "
             [:a (close-and-route data events/navigate-sign-up) "Sign Up"]])]
         (when (logged-in? data)
           [:ul.account-detail-expanded
            {:class
             (if (get-in data keypaths/account-menu-expanded)
               "open"
               "closed")}
            (when (own-store? data)
              (list
               [:li
                [:a (close-and-route data events/navigate-stylist-dashboard-commissions) "Dashboard"]]
               [:li
                [:a (close-and-route data events/navigate-stylist-manage-account) "Manage Account"]]
               [:li
                [:a {:href (get-in data keypaths/community-url)
                     :on-click (utils/send-event-callback data events/external-redirect-community)}
                 "Stylist Community"]]))
            (when-not (own-store? data)
              (list
               [:li
                [:a (close-and-route data events/navigate-account-referrals) "Refer A Friend"]]
               [:li
                [:a (close-and-route data events/navigate-account-manage) "Manage Account"]]))
            [:li
             [:a (close-and-enqueue data events/control-sign-out)
              "Logout"]]])
         [:h2.horizontal-nav-title
          (store :store_name)]
         [:ul.horizontal-nav-menu
          [:li
           [:a
            (if (own-store? data)
              (close-and-enqueue data events/control-menu-expand
                                 {:keypath keypaths/shop-menu-expanded})
              (shop-now-attrs data))
            (if (own-store? data)
              "Shop "
              "Shop")
            (when (own-store? data)
              [:figure.down-arrow])]]
          [:li [:a (close-and-route data events/navigate-guarantee) "30 Day Guarantee"]]
          [:li [:a (close-and-route data events/navigate-help) "Customer Service"]]]]
        (when (get-in data keypaths/shop-menu-expanded)
          [:ul.shop-menu-expanded.open
           [:li
            [:a
             (shop-now-attrs data)
             "Hair Extensions"]]
           [:li
            [:a
             (when-let [path (default-stylist-taxon-path data)]
               (close-and-route data events/navigate-category
                                {:taxon-path path}))
             "Stylist Only Products"]]])
        [:ul.slideout-nav-list
         (when (show-store-credit? data)
           [:li.slideout-nav-section
            [:h4.store-credit
             [:span.label "Available store credit:"]
             [:span.value
              (as-money (get-in data keypaths/user-total-available-store-credit))]]])
         (when (own-store? data)
           [:li.slideout-nav-section.stylist
            [:h3.slideout-nav-section-header.highlight "Manage Store"]
            (slideout-nav-link
             data
             (merge (close-and-route data events/navigate-stylist-dashboard-commissions)
                    {:icon-class "stylist-dashboard"
                     :label "Dashboard"
                     :full-width? false}))
            (slideout-nav-link
             data
             (merge (close-and-route data events/navigate-stylist-manage-account)
                    {:icon-class "edit-profile"
                     :label "Edit Profile"
                     :full-width? false}))
            (slideout-nav-link
             data
             {:href (get-in data keypaths/community-url)
              :on-click (utils/send-event-callback data events/external-redirect-community)
              :icon-class "community"
              :label "Stylist Community"
              :full-width? true})])
         [:li.slideout-nav-section
          [:h3.slideout-nav-section-header "Shop"]
          (slideout-nav-link
           data
           (merge
            (shop-now-attrs data)
            {:icon-class "hair-extensions"
             :label "Hair Extensions"
             :full-width? true}))
          (when (own-store? data)
            (slideout-nav-link
             data
             (merge
              (when-let [path (default-stylist-taxon-path data)]
                (close-and-route data events/navigate-category
                                 {:taxon-path path}))
              {:icon-class "stylist-products"
               :label "Stylist Products"
               :full-width? true})))]
         [:li.slideout-nav-section
          [:h3.slideout-nav-section-header "My Account"]
          (if (logged-in? data)
            [:div
             (when-not (own-store? data)
               (slideout-nav-link
                data
                (merge (close-and-route data events/navigate-account-referrals)
                       {:icon-class "refer-friend"
                        :label "Refer A Friend"
                        :full-width? true})))
             (slideout-nav-link
              data
              (merge (if (own-store? data)
                       (close-and-route data events/navigate-stylist-manage-account)
                       (close-and-route data events/navigate-account-manage))
                     {:icon-class "manage-account"
                      :label "Manage Account"
                      :full-width? false}))
             (slideout-nav-link
              data
              (merge (close-and-enqueue data events/control-sign-out)
                     {:icon-class "logout"
                      :label "Logout"
                      :full-width? false}))]
            [:div
             (slideout-nav-link
              data
              (merge (close-and-route data events/navigate-sign-in)
                     {:icon-class "sign-in"
                      :label "Sign In"
                      :full-width? false}))
             (slideout-nav-link
              data
              (merge (close-and-route data events/navigate-sign-up)
                     {:icon-class "join"
                      :label "Join"
                      :full-width? false}))])]
         [:li.slideout-nav-section
          [:h3.slideout-nav-section-header "Help"]
          (slideout-nav-link
           data
           (merge (close-and-route data events/navigate-help)
                  {:icon-class "customer-service"
                   :label "Customer Service"
                   :full-width? false}))
          (slideout-nav-link
           data
           (merge (close-and-route data events/navigate-guarantee)
                  {:icon-class "30-day-guarantee"
                   :label "30 Day Guarantee"
                   :full-width? false}))]]])])))
