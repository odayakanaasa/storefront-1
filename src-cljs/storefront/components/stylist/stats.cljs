(ns storefront.components.stylist.stats
  (:require [clojure.string :as str]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [storefront.components.formatters :as f]
            [storefront.components.utils :as utils]
            [storefront.components.svg :as svg]
            [storefront.events :as events]
            [storefront.keypaths :as keypaths]
            [storefront.messages :as messages]
            [swipe :as swipe]))

(defn choose-stat [data stat]
  (utils/send-event-callback data events/control-stylist-view-stat stat))

(defn choose-stat-now [data stat]
  (messages/send data events/control-stylist-view-stat stat))

(def ordered-stats [:previous-payout :next-payout :lifetime-payouts])
(def default-stat :next-payout) ; NOTE: this should match the `selected-stylist-stat` in `storefront.state`

(defn stat->idx [stat]
  (utils/position #(= % stat) ordered-stats))

(defn idx->stat [idx]
  (get ordered-stats idx default-stat))

(def default-idx (stat->idx default-stat))

(defn ^:private circle [selected]
  [:div.bg-white.circle
   {:class (when-not selected "bg-lighten-2")
    :style {:width "8px" :height "8px"}}])

(defn ^:private circle-for-stat [data selected stat]
  [:div.p1.pointer
   {:on-click (choose-stat data stat)}
   (circle (= selected stat))])

(defn ^:private money [amount]
  (f/as-money-without-cents amount))

(defn ^:private money-with-cents [amount]
  [:div.flex.justify-center
   (money amount)
   [:span.h5 {:style {:margin "5px 3px"}} (f/as-money-cents-only amount)]])

(def payday 3) ;; 3 -> Wednesday in JS

(defn days-till-payout []
  (let [dow (.getDay (js/Date.))]
    (mod (+ 7 payday (- dow))
         7)))

(defn in-x-days []
  (let [days (days-till-payout)]
    (condp = days
      0 "today"
      1 "tomorrow"
      (str "in " days " days"))))

(def stat-card "left col-12 relative")

(defmulti render-stat (fn [keypath stat] keypath))

(defmethod render-stat keypaths/stylist-stats-previous-payout [_ {:keys [amount date]}]
  [:div.my3
   {:class stat-card}
   [:div.p1 "LAST PAYOUT"]
   (if (> amount 0)
     (list
      [:div.py2.h00 {:style {:margin-left "-5px"}} (money-with-cents amount)]
      [:div (f/long-date date)])
     (list
      [:div {:style {:padding "18px"}} svg/large-payout]
      [:div "Tip: Your last payout will show here."]))])

(defmethod render-stat keypaths/stylist-stats-next-payout [_ {:keys [amount]}]
  [:div.my3
   {:class stat-card}
   [:div.p1 "NEXT PAYOUT"]
   (if (> amount 0)
     (list
      [:div.py2.h00 {:style {:margin-left "-5px"}}(money-with-cents amount)]
      [:div "Payment " (in-x-days)])
     (list
      [:div.py2 svg/large-dollar]
      [:div "Tip: Your next payout will show here."]))])

(defmethod render-stat keypaths/stylist-stats-lifetime-payouts [_ {:keys [amount]}]
  [:div.my3
   {:class stat-card}
   [:div.p1 "LIFETIME COMMISSIONS"]
   (if (> amount 0)
     (list
      [:div.py2.h00 {:style {:margin-left "-5px"}} (money amount)]
      [:div utils/nbsp])
     (list
      [:div.py2 svg/large-percent]
      [:div "Tip: Lifetime commissions will show here."]))])

(defn stylist-dashboard-stats-component [data owner]
  (reify
    om/IDidMount
    (did-mount [this]
      (om/set-state!
       owner
       {:swiper (js/Swipe. (om/get-node owner "stats")
                           #js {:continuous false
                                :startSlide (or (stat->idx (get-in data keypaths/selected-stylist-stat))
                                                default-idx)
                                :callback (fn [idx _]
                                            (choose-stat-now data (idx->stat idx)))})}))
    om/IWillUnmount
    (will-unmount [this]
      (when-let [swiper (:swiper (om/get-state owner))]
        (.kill swiper)))
    om/IRenderState
    (render-state [_ {:keys [swiper]}]
      (html
       (let [selected (get-in data keypaths/selected-stylist-stat)
             selected-idx (stat->idx selected)]
         (when (and swiper selected-idx)
           (let [delta (- (.getPos swiper) selected-idx)]
             (if (pos? delta)
               (dotimes [_ delta] (.prev swiper))
               (dotimes [_ (- delta)] (.next swiper)))))
         [:div.py1.bg-teal-gradient.white.center.sans-serif
          [:div.overflow-hidden.relative
           {:ref "stats"}
           [:div.overflow-hidden.relative.engrave-2
            (for [stat ordered-stats]
              (let [keypath (conj keypaths/stylist-stats stat)]
                (render-stat keypath (get-in data keypath))))]]

          [:div.flex.justify-center
           (for [stat ordered-stats]
             (circle-for-stat data selected stat))]])))))
