(ns storefront.components.sign-up
  (:require #?(:clj [storefront.component-shim :as component]
               :cljs [storefront.component :as component])
            [storefront.components.facebook :as facebook]
            [storefront.components.ui :as ui]
            [storefront.platform.component-utils :as utils]
            [storefront.events :as events]
            [storefront.keypaths :as keypaths]))

(defn component
  [{:keys [facebook-loaded?
           field-errors
           email
           password
           show-password?]} _ _]
  (component/create
   (ui/narrow-container
    [:h2.center.mt2.mb3.navy "Sign up for an account"]

    (facebook/sign-in-button facebook-loaded?)

    [:div.h5.center.gray.light.my2 "OR"]

    [:form.col-12.flex.flex-column.items-center
     {:on-submit (utils/send-event-callback events/control-sign-up-submit)}

     (ui/text-field {:auto-focus "autofocus"
                     :data-test  "user-email"
                     :errors     (get field-errors ["email"])
                     :keypath    keypaths/sign-up-email
                     :label      "Email"
                     :name       "email"
                     :required   true
                     :type       "email"
                     :value      email})

     (ui/text-field {:data-test "user-password"
                     :errors    (get field-errors ["password"])
                     :keypath   keypaths/sign-up-password
                     :label     "Password"
                     :name      "password"
                     :required  true
                     :type      "password"
                     :value     password
                     :hint      (when show-password? password)})

     [:div.gray.mtn3.mb2.col-12.left
      (ui/check-box {:label   "Show password"
                     :keypath keypaths/account-show-password?
                     :value   show-password?})]

     (ui/submit-button "Sign Up"
                       {:data-test "user-submit"})

     [:div.center.gray.mt3.mb2 "Already have an account? "
      [:a.teal (utils/route-to events/navigate-sign-in) "Log In"]]])))

(defn query [data]
  {:email                 (get-in data keypaths/sign-up-email)
   :password              (get-in data keypaths/sign-up-password)
   :show-password?        (get-in data keypaths/account-show-password? true)
   :facebook-loaded?      (get-in data keypaths/loaded-facebook)
   :field-errors          (get-in data keypaths/field-errors)})

(defn built-component [data opts]
  (component/build component (query data) nil))
