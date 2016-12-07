(ns ^{:author "Daniel Leong"
      :doc "events"}
  hangr.events
  (:require
    [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v
                           after debug]]
    [cljs.spec :as s]
    [hangr.db :refer [default-value]]
    [hangr.util :refer [key->id id->key]]
    [hangr.util.msg :refer [html->msg]]))


;; -- Interceptors ------------------------------------------------------------
;;

;; -- Helpers -----------------------------------------------------------------


;; -- Event Handlers ----------------------------------------------------------

(reg-event-db
  :initialize-db
  (fn [db _]
    (assoc default-value
           :page
           (:page (or db
                      default-value)))))

(reg-event-db
  :navigate
  [trim-v]
  (fn [db page]
    (assoc db :page page)))

(reg-event-db
  :connected
  (fn [db _]
    (assoc db :connecting? false)))

;;
;; Update a conversation. This may trigger
;;  a fetch of people information
(reg-event-fx
  :update-conv
  [trim-v]
  (fn [{:keys [db]} [conv]]
    ;; (println "Update conv" conv)
    ;; (when-not (-> db :people ))
    {:db (assoc-in db 
                   [:convs (:id conv)]
                   conv)
     ;; scroll to bottom if a conversation
     :scroll-to-bottom (= :conv
                          (first (:page db)))}))

(reg-event-db
  :set-self
  [trim-v]
  (fn [db [info]] 
    (assoc db :self info)))

;; -- Events ------------------------------------------------------------------

(reg-event-fx
  :select-conv
  [trim-v]
  (fn [_ [conv-id]]
    {:ipc [:select-conv conv-id]}))

(reg-event-fx
  :send-html
  [trim-v]
  (fn [_ [conv-id msg-html]]
    {:ipc [:send conv-id (html->msg msg-html)]}))

(reg-event-fx
  :open-external
  [trim-v]
  (fn [_ [url]]
    {:open-external url}))

(reg-event-fx
  :scroll-to-bottom
  (fn [_ _]
    {:scroll-to-bottom :do!}))
