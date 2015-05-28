(ns storefront.transitions
  (:require [storefront.events :as events]
            [storefront.keypaths :as keypaths]
            [storefront.routes :as routes]
            [storefront.taxons :refer [taxon-path-for]]))

(defn clear-fields [app-state & fields]
  (reduce #(assoc-in %1 %2 "") app-state fields))

(defmulti transition-state identity)
(defmethod transition-state [] [dispatch event args app-state]
  ;; (js/console.log (clj->js event) (clj->js args)) ;; enable to see all events
  app-state)
(defmethod transition-state :default [dispatch event args app-state]
  ;; (js/console.log "IGNORED transition" (clj->js event) (clj->js args)) ;; enable to see ignored transitions
  app-state)

(defmethod transition-state events/navigate [_ event args app-state]
  (assoc-in app-state keypaths/navigation-event event))

(defmethod transition-state events/navigate-category [_ event {:keys [taxon-path]} app-state]
  (assoc-in app-state keypaths/browse-taxon-query {taxon-path-for taxon-path}))

(defmethod transition-state events/navigate-product [_ event {:keys [product-path query-params]} app-state]
  (let [taxon-id (js/parseInt (:taxon_id query-params))]
    (-> app-state
        (assoc-in keypaths/browse-taxon-query {:id taxon-id})
        (assoc-in keypaths/browse-product-query {:slug product-path})
        (assoc-in keypaths/browse-variant-query nil)
        (assoc-in keypaths/browse-variant-quantity 1)
        (assoc-in keypaths/browse-recently-added-variants []))))

(defmethod transition-state events/navigate-reset-password [_ event {:keys [reset-token]} app-state]
  (assoc-in app-state keypaths/reset-password-token reset-token))

(defmethod transition-state events/navigate-manage-account [_ event args app-state]
  (assoc-in app-state
            keypaths/manage-account-email
            (get-in app-state keypaths/user-email)))

(defmethod transition-state events/navigate-checkout-address [_ event args app-state]
  (-> app-state
      (assoc-in keypaths/checkout-current-step "address")
      (update-in keypaths/checkout-billing-address merge (get-in app-state keypaths/billing-address))
      (update-in keypaths/checkout-shipping-address merge (get-in app-state keypaths/shipping-address))))

(defmethod transition-state events/navigate-checkout-delivery [_ event args app-state]
  (assoc-in app-state keypaths/checkout-current-step "delivery"))

(defmethod transition-state events/control-menu-expand [_ event args app-state]
  (assoc-in app-state keypaths/menu-expanded true))

(defmethod transition-state events/control-menu-collapse [_ event args app-state]
  (assoc-in app-state keypaths/menu-expanded false))

(defmethod transition-state events/control-account-menu-expand [_ event args app-state]
  (assoc-in app-state keypaths/account-menu-expanded true))

(defmethod transition-state events/control-account-menu-collapse [_ event args app-state]
  (assoc-in app-state keypaths/account-menu-expanded false))

(defmethod transition-state events/control-sign-in-change [_ event args app-state]
  (update-in app-state keypaths/sign-in merge args))

(defmethod transition-state events/control-sign-up-change [_ event args app-state]
  (update-in app-state keypaths/sign-up merge args))

(defmethod transition-state events/control-sign-out [_ event args app-state]
  ;; FIXME clear other user specific pieces of state
  (-> app-state
      (assoc-in keypaths/user {})
      (assoc-in keypaths/order nil)))

(defmethod transition-state events/control-manage-account-change [_ event args app-state]
  (update-in app-state keypaths/manage-account merge args))

(defmethod transition-state events/control-change-state
  [_ event {:keys [state value]} app-state]
  (assoc-in app-state state value))

(defmethod transition-state events/control-browse-variant-select [_ event {:keys [variant]} app-state]
  (assoc-in app-state keypaths/browse-variant-query {:id (variant :id)}))

(defmethod transition-state events/control-browse-add-to-bag [_ event args app-state]
  app-state)

(defmethod transition-state events/control-counter-inc [_ event args app-state]
  (update-in app-state (:path args) inc))

(defmethod transition-state events/control-counter-dec [_ event args app-state]
  (update-in app-state (:path args) (comp (partial max 1) dec)))

(defmethod transition-state events/control-counter-set [_ event {:keys [path value-str]} app-state]
  (assoc-in app-state path
            (-> (js/parseInt value-str 10)
                (Math/abs)
                (max 1))))

(defmethod transition-state events/control-cart-coupon-change [_ event {coupon-code :coupon-code} app-state]
  (assoc-in app-state keypaths/cart-coupon-code coupon-code))

(defmethod transition-state events/control-forgot-password-change [_ event args app-state]
  (update-in app-state keypaths/forgot-password merge args))

(defmethod transition-state events/control-reset-password-change [_ event args app-state]
  (update-in app-state keypaths/reset-password merge args))

(defmethod transition-state events/control-checkout-change [_ event args app-state]
  (reduce-kv (fn [m k v]
               (assoc-in app-state k v))
             app-state
             args))

(defmethod transition-state events/control-checkout-shipping-method-select [_ event {id :id} app-state]
  (assoc-in app-state keypaths/checkout-selected-shipping-method-id id))

(defmethod transition-state events/api-success-taxons [_ event args app-state]
  (assoc-in app-state keypaths/taxons (:taxons args)))

(defmethod transition-state events/api-success-store [_ event args app-state]
  (assoc-in app-state keypaths/store args))

(defmethod transition-state events/api-success-products [_ event {:keys [taxon-path products]} app-state]
  (update-in app-state keypaths/products
             merge
             (->> products
                  (mapcat (fn [p] [(:id p) p]))
                  (apply hash-map))))

(defmethod transition-state events/api-success-product [_ event {:keys [product-path product]} app-state]
  (-> app-state
      (assoc-in keypaths/browse-product-query {:slug product-path})
      (assoc-in (conj keypaths/products (:id product)) product)))

(defmethod transition-state events/api-success-states [_ event {:keys [states]} app-state]
  (assoc-in app-state keypaths/states states))

(defmethod transition-state events/api-success-stylist-commissions
  [_ event {:keys [rate next-amount paid-total new-orders payouts]} app-state]
  (-> app-state
      (assoc-in keypaths/stylist-commissions-rate rate)
      (assoc-in keypaths/stylist-commissions-next-amount next-amount)
      (assoc-in keypaths/stylist-commissions-paid-total paid-total)
      (assoc-in keypaths/stylist-commissions-new-orders new-orders)
      (assoc-in keypaths/stylist-commissions-payouts payouts)))

(defmethod transition-state events/api-success-stylist-bonus-credits
  [_ event {:keys [bonuses bonus-amount earning-amount commissioned-revenue total-credit available-credit]} app-state]
  (-> app-state
      (assoc-in keypaths/stylist-bonus-credit-bonuses bonuses)
      (assoc-in keypaths/stylist-bonus-credit-bonus-amount bonus-amount)
      (assoc-in keypaths/stylist-bonus-credit-earning-amount earning-amount)
      (assoc-in keypaths/stylist-bonus-credit-commissioned-revenue commissioned-revenue)
      (assoc-in keypaths/stylist-bonus-credit-total-credit total-credit)
      (assoc-in keypaths/stylist-bonus-credit-available-credit available-credit)))

(defmethod transition-state events/api-success-stylist-referral-program
  [_ event {:keys [sales-rep-email bonus-amount earning-amount total-amount referrals]} app-state]
  (-> app-state
      (assoc-in keypaths/stylist-referral-program-bonus-amount bonus-amount)
      (assoc-in keypaths/stylist-referral-program-earning-amount earning-amount)
      (assoc-in keypaths/stylist-referral-program-total-amount total-amount)
      (assoc-in keypaths/stylist-referral-program-referrals referrals)
      (assoc-in keypaths/stylist-sales-rep-email sales-rep-email)))

(defn sign-in-user [app-state {:keys [email token store_slug id total_available_store_credit]}]
  (-> app-state
      (assoc-in keypaths/user-id id)
      (assoc-in keypaths/user-email email)
      (assoc-in keypaths/user-token token)
      (assoc-in keypaths/user-store-slug store_slug)
      (assoc-in keypaths/user-total-available-store-credit (js/parseFloat total_available_store_credit))))

(defmethod transition-state events/api-success-sign-in [_ event args app-state]
  (-> app-state
      (sign-in-user args)
      (clear-fields keypaths/sign-in-email
                    keypaths/sign-in-password)))

(defmethod transition-state events/api-success-sign-up [_ event args app-state]
  (-> app-state
      (sign-in-user args)
      (clear-fields keypaths/sign-up-email
                    keypaths/sign-up-password
                    keypaths/sign-up-password-confirmation)))

(defmethod transition-state events/api-success-forgot-password [_ event args app-state]
  (clear-fields app-state keypaths/forgot-password-email))

(defmethod transition-state events/api-success-reset-password [_ event args app-state]
  (-> app-state
      (sign-in-user args)
      (clear-fields keypaths/reset-password-password
                    keypaths/reset-password-password-confirmation
                    keypaths/reset-password-token)
      (assoc-in keypaths/sign-in-remember true)))

(defmethod transition-state events/api-success-create-order [_ event {:keys [number token]} app-state]
  (-> app-state
      (assoc-in keypaths/user-order-token token)
      (assoc-in keypaths/user-order-id number)))

(defmethod transition-state events/api-success-add-to-bag [_ event {:keys [variant-id variant-quantity]} app-state]
  (-> app-state
      (update-in keypaths/browse-recently-added-variants
                 conj
                 {:id variant-id
                  :quantity variant-quantity})))

(defmethod transition-state events/api-success-get-order [_ event order app-state]
  (-> app-state
      (assoc-in keypaths/checkout-selected-shipping-method-id (get-in order [:shipments 0 :selected_shipping_rate :id]))
      (assoc-in keypaths/order order)
      (assoc-in keypaths/cart-quantities
                (into {} (map (juxt :id :quantity) (order :line_items))))))

(defmethod transition-state events/api-success-manage-account [_ event args app-state]
  (-> app-state
      (sign-in-user args)
      (clear-fields keypaths/manage-account-email
                    keypaths/manage-account-password
                    keypaths/manage-account-password-confirmation)))

(defmethod transition-state events/api-success-account-update-addresses [_ event {:keys [billing-address shipping-address] :as args} app-state]
  (-> app-state
      (sign-in-user args)
      (merge {:billing-address billing-address
              :shipping-address shipping-address})
      (update-in keypaths/checkout-billing-address merge billing-address)
      (update-in keypaths/checkout-shipping-address merge shipping-address)))

(defmethod transition-state events/api-success-sms-number [_ event args app-state]
  (assoc-in app-state keypaths/sms-number (:number args)))

(defmethod transition-state events/api-success-update-cart [_ event order app-state]
  (-> app-state
      (assoc-in keypaths/order order)
      (assoc-in keypaths/cart-coupon-code "")))

(defmethod transition-state events/api-success-update-order [_ event order app-state]
  (assoc-in app-state keypaths/order order))

(defmethod transition-state events/api-success-promotions [_ event {promotions :promotions} app-state]
  (assoc-in app-state keypaths/promotions promotions))

(defmethod transition-state events/flash-show-success [_ event args app-state]
  (assoc-in app-state keypaths/flash-success (select-keys args [:message :navigation])))

(defmethod transition-state events/flash-dismiss-success [_ event args app-state]
  (assoc-in app-state keypaths/flash-success nil))
