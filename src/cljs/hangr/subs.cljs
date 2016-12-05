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
  :self) ;; NOTE: keywords are functions! In this shortcut, they act on db

(reg-sub
  :all-convs
  :convs)

(reg-sub
  :convs
  :<- [:self]
  :<- [:all-convs]
  (fn [[self convs] _]
    (when (and self convs)
      (->> convs
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
  :<- [:self]
  :<- [:all-convs]
  (fn [[self convs] [_ id]]
    (when (and self convs)
      (-> convs
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
