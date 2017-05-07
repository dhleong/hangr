(ns hangr.util.ui.markdown-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [hangr.util.ui.markdown :refer [markdown->hiccup]]))

(deftest basic-markdown->hiccup-test
  (testing "Simple pass-through"
    (is (= [:div.hello "world"]
           (markdown->hiccup
             :div.hello
             "world"))))
  (testing "Boldify"
    (is (= [:div [:b "itskaylee"]]
           (markdown->hiccup
             :div
             "**itskaylee**")))))

(deftest ul-markdown->hiccup-test
  (testing "Listify 1"
    (is (= [:div
            [:ul [:li.ul "itskaylee"]]]
           (markdown->hiccup
             :div
             "- itskaylee"))))
  (testing "Listify with bold"
    (is (= [:div
            [:ul [:li.ul
                  "its"
                  [:b "kay"]
                  "lee"]]]
           (markdown->hiccup
             :div
             "- its**kay**lee"))))
  (testing "Listify 2"
    (is (= [:div
            [:ul
             [:li.ul "itskaylee"]
             [:li.ul "mreynolds"]]]
           (markdown->hiccup
             :div
             "- itskaylee\n- mreynolds")))))

(deftest ol-markdown->hiccup-test
  ;; NOTE: the fancy stuff is already tested
  ;; above for ul; these use the same mechanism,
  ;; so we just make sure it parses
  (testing "Listify 1"
    (is (= [:div
            [:ol [:li.ol "itskaylee"]]]
           (markdown->hiccup
             :div
             "1. itskaylee")))))

(deftest github-issues->hiccup
  (testing "Ignore without the url"
    (is (= [:div "#42"]
           (markdown->hiccup
             :div
             "#42"))))
  (testing "Parse github issue links"
    (is (= [:div
            [:a
             {:href "https://github.com/firefly/serenity/issues/42"}
             "#42"]]
           (markdown->hiccup
             :div
             "#42"
             {:github
              "https://github.com/firefly/serenity"})))))

(deftest update-fns
  (testing
    (is (= [:div [:b.awesome "itskaylee"]]
           (markdown->hiccup
             :div
             "**itskaylee**"
             {:update
              {:b (fn [b]
                    (assoc b 0
                           :b.awesome))}})))))
