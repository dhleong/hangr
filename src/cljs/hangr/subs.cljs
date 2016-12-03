(ns ^{:author "Daniel Leong"
      :doc "Subscriptions"}
  hangr.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]))

(reg-sub
  :page
  (fn [db _]
    (if (:loading? db)
      ;; still loading; override the page
      [:connecting]
      (:page db))))

(reg-sub
  :convs
  (fn [db _]
    (->> db 
        :convs 
        vals
        (sort-by 
          (fn [conv]
            (-> conv :event last :timestamp))
          ;; compare in reverse order (higher timestamps first)
          #(compare %2 %1)))))
