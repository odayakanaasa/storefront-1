(ns storefront.hooks.uploadcare
  (:require [storefront.browser.tags :refer [insert-tag-with-callback src-tag]]
            [storefront.config :as config]
            [storefront.events :as events]
            [storefront.platform.messages :refer [handle-message]]
            [goog.object :as object]))

(defn insert []
  (when-not (.hasOwnProperty js/window "uploadcare")
    (set! js/UPLOADCARE_PUBLIC_KEY config/uploadcare-public-key)
    (insert-tag-with-callback
     (src-tag "https://ucarecdn.com/libs/widget/2.10.3/uploadcare.full.min.js"
              "uploadcare")
     #(handle-message events/inserted-uploadcare))))

(defn ^:private receive-file-info [file-info]
  (handle-message events/uploadcare-api-success-upload-image
                  {:file-info (js->clj file-info :keywordize-keys true)}))

(defn ^:private handle-error [error file-info]
  (handle-message events/uploadcare-api-failure
                  {:error error
                   :file-info (js->clj file-info :keywordize-keys true)}))

(defn ^:private handle-file [file]
  ;; Google Closure cannot detect externs correctly here...
  (let [promise (.call (object/get file "promise") file)
        fail (.call (object/get promise "fail") promise handle-error)]
    (.call (object/get fail "done") fail receive-file-info)))

(defn dialog []
  (when (.hasOwnProperty js/window "uploadcare")
    (-> js/uploadcare
        (.openDialog
         nil
         (clj->js {:imageShrink "1600x1600"
                   :imagesOnly  true
                   :crop        "1:1"}))
        (.done handle-file))))
