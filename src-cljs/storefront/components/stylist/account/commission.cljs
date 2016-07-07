(ns storefront.components.stylist.account.commission
  (:require [storefront.component :as component]
            [storefront.components.ui :as ui]
            [storefront.events :as events]
            [storefront.keypaths :as keypaths]
            [storefront.platform.component-utils :as utils]
            [storefront.request-keys :as request-keys]))

(defn component [{:keys [saving?
                         payout-method
                         payout-methods
                         venmo-phone
                         paypal-email
                         address1
                         address2
                         zipcode
                         city
                         state-id
                         states]} owner opts]
  (component/create
   [:form
    {:on-submit
     (utils/send-event-callback events/control-stylist-account-commission-submit)}
    [:div.col.col-12.md-up-col-6
     [:h1.h2.light.my3.center.col-12 "Update commission info"]

     [:div.col-12.md-up-col-10.mx-auto
      (ui/select-field "Payout Method"
                       (conj keypaths/stylist-manage-account :chosen_payout_method)
                       payout-method
                       payout-methods
                       {:id        "payout-method"
                        :data-test "payout-method"
                        :required  true})

      (condp = payout-method
        "venmo"         (ui/text-field "Venmo Phone #"
                                       (conj keypaths/stylist-manage-account :venmo_payout_attributes :phone)
                                       venmo-phone
                                       {:type      "tel"
                                        :name      "venmo-phone"
                                        :id        "venmo-phone"
                                        :data-test "venmo-phone"
                                        :required  true})
        "paypal"        (ui/text-field "PayPal Email"
                                       (conj keypaths/stylist-manage-account :paypal_payout_attributes :email)
                                       paypal-email
                                       {:type      "email"
                                        :name      "paypal-email"
                                        :id        "paypal-email"
                                        :data-test "paypal-email"
                                        :required  true})
        "mayvenn_debit" [:p.ml1.mb3 "A prepaid Visa debit card will be mailed to the address entered here"]
        "check"         [:p.ml1.mb3 "Checks will mail to the address entered here"]
        [:p.ml1.mb3 "Checks will mail to the address entered here"])]]


    [:div.col.col-12.md-up-col-6
     [:div.mx-auto.col-12.md-up-col-10
      [:div.border-dark-white.border-top.md-up-hide.mb3]
      (ui/text-field "Address"
                     (conj keypaths/stylist-manage-account :address :address1)
                     address1
                     {:autofocus "autofocus"
                      :type      "text"
                      :name      "account-address1"
                      :id        "account-address1"
                      :data-test "account-address1"
                      :required  true})

      [:div.flex.col-12
       [:div.col-6 (ui/text-field "Apt/Suite"
                                  (conj keypaths/stylist-manage-account :address :address2)
                                  address2
                                  {:type      "text"
                                   :name      "account-address2"
                                   :data-test "account-address2"
                                   :id        "account-address2"
                                   :class     "rounded-left"})]

       [:div.col-6 (ui/text-field "Zip Code"
                                  (conj keypaths/stylist-manage-account :address :zipcode)
                                  zipcode
                                  {:type      "text"
                                   :name      "account-zipcode"
                                   :id        "account-zipcode"
                                   :data-test "account-zipcode"
                                   :class     "rounded-right border-width-left-0"
                                   :required  true})]]

      (ui/text-field "City"
                     (conj keypaths/stylist-manage-account :address :city)
                     city
                     {:type      "text"
                      :name      "account-city"
                      :id        "account-city"
                      :data-test "account-city"
                      :required  true})

      (ui/select-field "State"
                       (conj keypaths/stylist-manage-account :address :state_id)
                       state-id
                       states
                       {:id          :account-state
                        :data-test   "account-state"
                        :placeholder "State"
                        :required    true})]]

    [:div.my2.col-12.clearfix
     ui/nbsp
     [:div.border-dark-white.border-top.to-md-hide.mb3]
     [:div.col-12.md-up-col-5.mx-auto
      (ui/submit-button "Update" {:spinning? saving?
                                  :data-test "account-form-submit"})]]]))

(defn payout-methods [original-payout-method]
  (cond-> [["Venmo" "venmo"]
           ["PayPal" "paypal"]
           ["Check" "check"]]
    (= original-payout-method "mayvenn_debit") (conj ["Mayvenn Debit" "mayvenn_debit"])))

(defn query [data]
  {:saving?        (utils/requesting? data request-keys/update-stylist-account-commission)
   :payout-method  (get-in data (conj keypaths/stylist-manage-account :chosen_payout_method))
   :payout-methods (payout-methods (get-in data (conj keypaths/stylist-manage-account :original_payout_method)))
   :paypal-email   (get-in data (conj keypaths/stylist-manage-account :paypal_payout_attributes :email))
   :venmo-phone    (get-in data (conj keypaths/stylist-manage-account :venmo_payout_attributes :phone))
   :address1       (get-in data (conj keypaths/stylist-manage-account :address :address1))
   :address2       (get-in data (conj keypaths/stylist-manage-account :address :address2))
   :city           (get-in data (conj keypaths/stylist-manage-account :address :city))
   :zipcode        (get-in data (conj keypaths/stylist-manage-account :address :zipcode))
   :state-id       (get-in data (conj keypaths/stylist-manage-account :address :state_id))
   :states         (map (juxt :name :id) (get-in data keypaths/states))})