(ns storefront.api
  (:require [ajax.core :refer [GET POST PUT DELETE json-response-format]]
            [clojure.set :refer [subset?]]
            [storefront.events :as events]
            [storefront.accessors.taxons :refer [taxon-name-from]]
            [clojure.set :refer [rename-keys]]
            [storefront.config :refer [api-base-url send-sonar-base-url send-sonar-publishable-key]]
            [storefront.request-keys :as request-keys]
            [storefront.utils.uuid :refer [random-uuid]]))

(defn default-error-handler [handle-message req-key req-id response]
  (handle-message events/api-end {:request-id req-id
                                  :request-key req-key})
  (cond
    ;; aborted request
    (#{:aborted} (:failure response))
    (handle-message events/api-abort)

    ;; connectivity
    (zero? (:status response))
    (handle-message events/api-failure-no-network-connectivity response)

    ;; standard rails error response
    (or (seq (get-in response [:response :error]))
        (seq (get-in response [:response :errors])))
    (handle-message events/api-failure-validation-errors
                    (-> (:response response)
                        (select-keys [:error :errors])
                        (rename-keys {:errors :fields})))

    ;; standard rails validation errors
    (or (seq (get-in response [:response :exception])))
    (handle-message events/api-failure-validation-errors
                    {:fields {"" [(get-in response [:response :exception])]}})

    ;; Standard waiter response
    (seq (get-in response [:response :error-code]))
    (handle-message events/api-failure-validation-errors
                    (-> (:response response)
                        (select-keys [:error-message])
                        (rename-keys {:error-message :error})))

    :else
    (handle-message events/api-failure-bad-server-response response)))

(defn filter-nil [m]
  (into {} (filter second m)))

(def default-req-opts {:headers {"Accepts" "application/json"}
                      :format :json
                      :response-format (json-response-format {:keywords? true})})

(defn merge-req-opts [handle-message req-key req-id {:keys [handler] :as request-opts}]
  (merge default-req-opts
         {:error-handler (partial default-error-handler handle-message req-key req-id)}
         request-opts
         {:handler (fn [res]
                     (handle-message events/api-end {:request-key req-key
                                                     :request-id req-id})
                     (handler res))}))

(defn api-req
  [handle-message method path req-key request-opts]
  (let [req-id (random-uuid)
        request
        (method (str api-base-url path)
                (merge-req-opts handle-message req-key req-id request-opts))]
    (handle-message events/api-start {:xhr request
                                      :request-key req-key
                                      :request-id req-id})))

(defn cache-req
  [cache handle-message method path req-key {:keys [handler params] :as request-opts}]
  (let [key [path params]
        res (cache key)]
    (if res
      (handler res)
      (api-req handle-message
               method
               path
               req-key
               (merge request-opts
                      {:handler
                       (fn [result]
                         (handle-message events/api-success-cache {key result})
                         (handler result))})))))

(defn get-taxons [handle-message cache]
  (cache-req
   cache
   handle-message
   GET
   "/product-nav-taxonomy"
   request-keys/get-taxons
   {:handler
    #(handle-message events/api-success-taxons (select-keys % [:taxons]))}))

(defn get-store [handle-message cache store-slug]
  (cache-req
   cache
   handle-message
   GET
   "/store"
   request-keys/get-store
   {:params
    {:store_slug store-slug}
    :handler
    #(handle-message events/api-success-store %)}))

(defn get-promotions [handle-message cache]
  (cache-req
   cache
   handle-message
   GET
   "/promotions"
   request-keys/get-promotions
   {:handler
    #(handle-message events/api-success-promotions %)}))

(defn get-products [handle-message cache taxon-path]
  (cache-req
   cache
   handle-message
   GET
   "/products"
   (conj request-keys/get-products taxon-path)
   {:params
    {:taxon_name (taxon-name-from taxon-path)}
    :handler
    #(handle-message events/api-success-products (merge (select-keys % [:products])
                                                        {:taxon-path taxon-path}))}))

(defn get-product [handle-message product-path]
  (api-req
   handle-message
   GET
   "/products"
   request-keys/get-product
   {:params {:slug product-path}
    :handler
    #(handle-message events/api-success-product {:product-path product-path
                                                 :product %})}))

(defn get-states [handle-message cache]
  (cache-req
   cache
   handle-message
   GET
   "/states"
   request-keys/get-states
   {:handler
    #(handle-message events/api-success-states (select-keys % [:states]))}))

(defn get-payment-methods [handle-message cache]
  (cache-req
   cache
   handle-message
   GET
   "/payment_methods"
   request-keys/get-payment-methods
   {:handler
    #(handle-message events/api-success-payment-methods (select-keys % [:payment_methods]))}))

(defn select-sign-in-keys [args]
  (select-keys args [:email :token :store_slug :id]))

(defn sign-in [handle-message email password stylist-id token]
  (api-req
   handle-message
   POST
   "/login"
   request-keys/sign-in
   {:params
    {:email email
     :password password
     :stylist-id stylist-id}
    :handler
    #(handle-message events/api-success-sign-in (select-sign-in-keys %))}))

(defn sign-up [handle-message email password password-confirmation stylist-id order-token]
  (api-req
   handle-message
   POST
   "/signup"
   request-keys/sign-up
   {:params
    {:email email
     :password password
     :password_confirmation password-confirmation
     :stylist-id stylist-id
     :order-token order-token}
    :handler
    #(handle-message events/api-success-sign-up (select-sign-in-keys %))}))

(defn forgot-password [handle-message email]
  (api-req
   handle-message
   POST
   "/forgot_password"
   request-keys/forgot-password
   {:params
    {:email email}
    :handler
    #(handle-message events/api-success-forgot-password)}))

(defn reset-password [handle-message password password-confirmation reset-token]
  (api-req
   handle-message
   POST
   "/reset_password"
   request-keys/reset-password
   {:params
    {:password password
     :password_confirmation password-confirmation
     :reset_password_token reset-token}
    :handler
    #(handle-message events/api-success-reset-password (select-sign-in-keys %))}))

(defn add-user-in-order [handle-message token number user-token user-id]
  (api-req
   handle-message
   POST
   "/v2/add-user-to-order"
   request-keys/add-user-in-order
   {:params
    {:user-id user-id
     :user-token user-token
     :number number
     :token token}
    :handler
    #(handle-message events/api-success-update-order {:order %})}))

(defn select-address-keys [m]
  (let [keys [:address1 :address2 :city :country_id :firstname :lastname :id :phone :state_id :zipcode]]
    (select-keys m keys)))

(defn rename-server-address-keys [m]
  (rename-keys m {:bill_address :billing-address
                  :ship_address :shipping-address}))

(defn get-account [handle-message id token stylist-id]
  (api-req
   handle-message
   GET
   "/users"
   request-keys/get-account
   {:params
    {:id id
     :token token
     :stylist-id stylist-id}
    :handler
    #(handle-message events/api-success-account (rename-server-address-keys %))}))

(defn update-account [handle-message id email password password-confirmation token]
  (api-req
   handle-message
   PUT
   "/users"
   request-keys/update-account
   {:params
    {:id id
     :email email
     :password password
     :password_confirmation password-confirmation
     :token token}
    :handler
    #(handle-message events/api-success-manage-account (select-sign-in-keys %))}))

(defn update-account-address [handle-message id email billing-address shipping-address token]
  (api-req
   handle-message
   PUT
   "/users"
   request-keys/update-account-address
   {:params
    {:id id
     :email email
     :bill_address (select-address-keys billing-address)
     :ship_address (select-address-keys shipping-address)
     :token token}
    :handler
    #(handle-message events/api-success-address (rename-server-address-keys %))}))

(defn select-stylist-account-keys [args]
  (select-keys args [:birth_date_1i :birth_date_2i :birth_date_3i
                     :profile_picture_url
                     :chosen_payout_method
                     :venmo_payout_attributes
                     :paypal_payout_attributes
                     :instagram_account
                     :styleseat_account
                     :user
                     :address]))

(defn get-stylist-account [handle-message user-token]
  (api-req
   handle-message
   GET
   "/stylist"
   request-keys/get-stylist-account
   {:params
    {:user-token user-token}
    :handler
    #(handle-message events/api-success-stylist-manage-account
                     {:updated false
                      :stylist (select-stylist-account-keys %)})}))

(defn update-stylist-account [handle-message user-token stylist-account]
  (api-req
   handle-message
   PUT
   "/stylist"
   request-keys/update-stylist-account
   {:params
    {:user-token user-token
     :stylist stylist-account}
    :handler
    #(handle-message events/api-success-stylist-manage-account
                     {:updated true
                      :stylist (select-stylist-account-keys %)})}))

(defn update-stylist-account-profile-picture [handle-message user-token profile-picture]
  (let [form-data (doto (js/FormData.)
                    (.append "file" profile-picture (.-name profile-picture))
                    (.append "user-token" user-token))]
    (PUT (str api-base-url "/stylist/profile-picture")
        {:handler #(handle-message events/api-success-stylist-manage-account-profile-picture
                                   (merge {:updated true}
                                          {:stylist (select-keys % [:profile_picture_url])}))
         :error-handler (partial default-error-handler handle-message)
         :params form-data
         :response-format (json-response-format {:keywords? true})
         :timeout 10000})))

(defn get-stylist-commissions [handle-message user-token]
  (api-req
   handle-message
   GET
   "/stylist/commissions"
   request-keys/get-stylist-commissions
   {:params
    {:user-token user-token}
    :handler
    #(handle-message events/api-success-stylist-commissions
                     (select-keys % [:rate :next-amount :paid-total :new-orders :payouts]))}))

(defn get-stylist-bonus-credits [handle-message user-token]
  (api-req
   handle-message
   GET
   "/stylist/bonus-credits"
   request-keys/get-stylist-bonus-credits
   {:params
    {:user-token user-token}
    :handler
    #(handle-message events/api-success-stylist-bonus-credits
                     (select-keys % [:bonus-amount
                                     :earning-amount
                                     :commissioned-revenue
                                     :total-credit
                                     :available-credit
                                     :bonuses]))}))

(defn get-stylist-referral-program [handle-message user-token]
  (api-req
   handle-message
   GET
   "/stylist/referrals"
   request-keys/get-stylist-referral-program
   {:params
    {:user-token user-token}
    :handler
    #(handle-message events/api-success-stylist-referral-program
                     (select-keys % [:sales-rep-email :bonus-amount :earning-amount :total-amount :referrals]))}))

(defn get-sms-number [handle-message]
  (letfn [(normalize-number [x] ;; smooth out send-sonar's two different number formats
            (apply str (if (= "+" (first x))
                         (drop 3 x)
                         x)))
          (callback [resp]
            (handle-message events/api-success-sms-number
                            {:number (-> resp :available_number normalize-number)}))]
    (GET (str send-sonar-base-url "/phone_numbers/available")
        {:handler callback
         :headers {"Accepts" "application/json"
                   "X-Publishable-Key" send-sonar-publishable-key}
         :format :json
         :response-format (json-response-format {:keywords? true})})))

(defn create-order [handle-message stylist-id user-token]
  (api-req
   handle-message
   POST
   "/orders"
   request-keys/create-order
   {:params
    (merge {:stylist-id stylist-id}
           (if user-token {:token user-token} {}))
    :handler
    #(handle-message events/api-success-create-order (select-keys % [:number :token]))}))

(defn create-order-if-needed [handle-message stylist-id order-id order-token user-token]
  (if (and order-token order-id)
    (handle-message events/api-success-create-order {:number order-id :token order-token})
    (create-order handle-message stylist-id user-token)))

(defn- update-cart-helper
  [handle-message user-token order-token order request-key success-handler]
  (api-req
   handle-message
   PUT
   "/cart"
   request-key
   {:params
    (filter-nil
     {:order (select-keys order [:number :line_items_attributes :coupon_code :email :user_id :state])
      :order_token order-token})
    :handler success-handler}))

(defn- update-line-item [handle-message user-token order line-item-id request-key f]
  (let [line-item (first (filter #(= (:id %) line-item-id) (:line_items order)))
        updated-line-item (select-keys (update line-item :quantity f)
                                       [:quantity :id :variant_id])
        updated-order (select-keys (merge order {:line_items_attributes [updated-line-item]
                                         :state "cart"})
                           [:line_items_attributes :number])]
    (update-cart-helper
     handle-message
     user-token
     (:token order)
     updated-order
     (conj request-key line-item-id)
     #(handle-message events/api-success-cart-update-line-item
                      {:order %}))))

(defn inc-line-item [handle-message order {:keys [variant-id] :as params}]
  (api-req
   handle-message
   POST
   "/v2/add-to-bag"
   (conj request-keys/increment-line-item variant-id)
   {:params (merge (select-keys order [:number :token])
                   {:variant-id variant-id
                    :quantity 1})
    :handler #(handle-message events/api-success-add-to-bag {:order %
                                                             :requested-quantity 1})}))

(defn dec-line-item [handle-message order {:keys [variant-id]}]
  (api-req
   handle-message
   POST
   "/v2/remove-from-bag"
   (conj request-keys/decrement-line-item variant-id)
   {:params (merge (select-keys order [:number :token])
                   {:variant-id variant-id
                    :quantity 1})
    :handler #(handle-message events/api-success-add-to-bag {:order %})}))

(defn delete-line-item [handle-message order variant-id]
  (api-req
   handle-message
   POST
   "/v2/remove-from-bag"
   (conj request-keys/delete-line-item variant-id)
   {:params (merge (select-keys order [:number :token])
                   {:variant-id variant-id
                    :quantity (get-in order [:line-items (keyword variant-id) :quantity])})
    :handler #(handle-message events/api-success-add-to-bag {:order %})}))

(defn update-coupon [handle-message user-token {order-token :token :as order} {:keys [coupon_code]}]
  (update-cart-helper handle-message
                      user-token
                      (:token order)
                      {:coupon_code coupon_code
                       :number (:number order)}
                      request-keys/update-coupon
                      #(handle-message events/api-success-cart-update-coupon
                                       {:order (rename-keys % {:token :token})})))
(defn update-addresses [handle-message user-token order]
  (api-req
   handle-message
   PUT
   "/v2/update-addresses"
   request-keys/update-addresses
   {:params (select-keys order [:number :token :email :billing-address :shipping-address])
    :handler #(handle-message events/api-success-update-order
                              {:order %
                               :navigate events/navigate-checkout-delivery})}))


(defn update-order [handle-message user-token order extra-message-args]
  (api-req
   handle-message
   PUT
   "/orders"
   request-keys/update-order
   {:params
    {:order (filter-nil (-> order
                            (select-keys [:number
                                          :bill_address
                                          :ship_address
                                          :shipments_attributes
                                          :payments_attributes
                                          :session_id
                                          :email])
                            (update-in [:bill_address] select-address-keys)
                            (update-in [:ship_address] select-address-keys)
                            (rename-keys {:token :token
                                          :bill_address :bill_address_attributes
                                          :ship_address :ship_address_attributes})))
     :use_store_credits (:use-store-credits order)
     :state (:state order)
     :order_token (:token order)}
    :handler
    #(handle-message events/api-success-update-order
                     (merge {:order %}
                            extra-message-args))}))


(defn get-order [handle-message number token]
  (api-req
   handle-message
   GET
   "/v2/order-for-number"
   request-keys/get-order
   {:params
    {:number number
     :token token}
    :handler
    #(handle-message events/api-success-get-order %)}))

(defn get-past-order [handle-message order-number user-token]
  (api-req
   handle-message
   GET
   "/orders"
   request-keys/get-past-order
   {:params
    {:id order-number
     :token user-token}
    :handler
    #(handle-message events/api-success-get-past-order %)}))

(defn get-my-orders [handle-message user-token]
  (api-req
   handle-message
   GET
   "/my_orders"
   request-keys/get-my-orders
   {:params
    {:user-token user-token}
    :handler
    #(handle-message events/api-success-my-orders %)}))

(defn api-failure? [event]
  (= events/api-failure (subvec event 0 2)))

(defn add-to-bag [handle-message {:keys [token number variant], :as params}]
  (api-req
   handle-message
   POST
   "/v2/add-to-bag"
   request-keys/add-to-bag
   {:params (merge (select-keys params [:quantity :stylist-id :user-id :user-token])
                   {:variant-id (:id variant)}
                   (when (and token number) {:token token :number number}))
    :handler #(handle-message events/api-success-add-to-bag
                              {:order %
                               :requested (select-keys params [:quantity :product :variant])})}))
