(ns ^{:author "Daniel Leong"
      :doc "Subscriptions"}
  hangr.subs
  (:require [re-frame.core :refer [reg-sub trim-v subscribe]]))

;; -- Simple subscriptions ----------------------------------------------------
;; NOTE: keywords are functions! In this shortcut, they act on db
;;  to just return a specific key

(reg-sub :connecting?  :connecting?)
(reg-sub :self :self) 

;; -- Internal subscriptions --------------------------------------------------
;; Note the use of namespaced keywords to get the raw field. Actual
;;  subscriptions should use the non-namespaced version to get the
;;  appropriate "view"

(reg-sub ::convs :convs)
(reg-sub ::page :page)

;; -- Public subscriptions ----------------------------------------------------

(reg-sub
  :page
  :<- [:connecting?]
  :<- [:self]
  :<- [::convs]
  :<- [::page]
  (fn [[connecting? self convs page] _]
    (println "!!!!PAGE=" page)
    (cond
      ; still connecting; override the page
      ; HACK: only show "connecting" for the friends list
      (and (= :friends (:page page)) connecting?) 
      [:connecting]
      ; still loading core data; override the page
      (or (nil? self) (nil? convs))
      [:loading]
      ; ready to go!
      :else 
      page)))

(reg-sub
  :convs
  :<- [:self]
  :<- [::convs]
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
  :<- [::convs]
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
                      (and
                        (not= (:sender %)
                              (:id self))
                        (not (:client-generated-id %))))))))))
