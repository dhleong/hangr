(ns ^{:author "Daniel Leong"
      :doc "Subscriptions"}
  hangr.subs
  (:require [re-frame.core :refer [reg-sub trim-v subscribe]]))

(reg-sub
  :page
  (fn [db _]
    (if (:loading? db)
      ;; still loading; override the page
      [:connecting]
      (:page db))))

(reg-sub
  :self
  (fn [db _]
    (:self db)))

(reg-sub
  :convs
  (fn [db _]
    (when-let [self (:self db)]
      (->> db 
           :convs 
           vals
           ;; filter out conversations we're not part of
           (filter
             (fn [conv]
               (contains?
                 (set (map :id (:members conv)))
                 (:id self))))
           ;; sort with most recent first
           (sort-by 
             (fn [conv]
               (-> conv :events last :timestamp))
             ;; compare in reverse order (higher timestamps first)
             #(compare %2 %1))))))

(reg-sub
  :conv
  (fn [db [_ id]]
    (when-let [self (:self db)] 
      (-> db
          :convs
          (get id)
          ;; insert :incoming? key as appropriate
          (update-in
            [:events]
            (partial
              map 
              #(assoc %
                      :incoming?
                      (not= (:sender %)
                            (:id self)))))))))
