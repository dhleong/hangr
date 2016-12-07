(ns hangr.util.msg-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [hangr.util.msg :refer [html->msg]]))

(deftest html->msg-test
  (testing "Single line"
    (is (= [[:text "Inara Serra"]] (html->msg "Inara Serra"))))
  (testing "Multi-line"
    (is (= [[:text "Inara Serra"]
            [:newline]
            [:text "Mal Reynolds"]] 
           (html->msg "Inara Serra<br/><br/>Mal Reynolds"))))
  (testing "Single link"
    (is (= [[:link "www.serenity.net"]] 
           (html->msg "www.serenity.net")))
    (is (= [[:text "Hey guys "]
            [:link "www.serenity.net"]] 
           (html->msg "Hey guys www.serenity.net")))
    (is (= [[:text "Hey guys "]
            [:link "www.serenity.net"]
            [:text " is our new site"]] 
           (html->msg "Hey guys www.serenity.net is our new site"))))
  (testing "Multi link"
    (is (= [[:link "www.serenity.net"]
            [:text " "]
            [:link "www.alliance.com"]] 
           (html->msg "www.serenity.net www.alliance.com")))
    (is (= [[:text "post "]
            [:link "www.serenity.net"]
            [:text " on "]
            [:link "www.alliance.com"]] 
           (html->msg "post www.serenity.net on www.alliance.com")))
    (is (= [[:text "post "]
            [:link "www.serenity.net"]
            [:text " on "]
            [:link "www.alliance.com"]
            [:text " guys"]]
           (html->msg "post www.serenity.net on www.alliance.com guys")))))

