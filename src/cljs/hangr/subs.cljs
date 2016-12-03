(ns ^{:author "Daniel Leong"
      :doc "Subscriptions"}
  hangr.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]))

(reg-sub
  :page
  (fn [db _]
    (:page db)))
