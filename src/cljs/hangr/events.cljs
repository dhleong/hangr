(ns ^{:author "Daniel Leong"
      :doc "events"}
  hangr.events
  (:require
    [hangr.db :refer [default-value]]
    [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v
                           after debug]]
    [cljs.spec :as s]))


;; -- Interceptors --------------------------------------------------------------
;;

;; -- Helpers -----------------------------------------------------------------


;; -- Event Handlers ----------------------------------------------------------

(reg-event-db
  :initialize-db
  (fn [db _]
    default-value))

(reg-event-db
  :navigate
  [trim-v]
  (fn [db page]
    (assoc db :page page)))

(reg-event-db
  :connected
  (fn [db _]
    (assoc db :loading? false)))
