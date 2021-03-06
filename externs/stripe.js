var Stripe = function() {};
Stripe.setPublishableKey = function() {};
Stripe.card = {};
Stripe.card.createToken = function() {};
Stripe.applePay = {};
Stripe.applePay.checkAvailability = function() {};
/**
 * @param {Object<string, *>} paymentRequest
 * @param {function(!_StripeApplePayResult,function(*))} callback
 * @return {!_ApplePaySession}
 */
Stripe.applePay.buildSession = function(paymentRequest, callback) {};

function _ApplePaySession() {};
_ApplePaySession.prototype.begin = function() {};
_ApplePaySession.prototype.onshippingcontactselected = null;
_ApplePaySession.prototype.onshippingmethodselected = null;
_ApplePaySession.prototype.oncancel = null;

_ApplePaySession.prototype.completeShippingContactSelection = function() {};
_ApplePaySession.prototype.completeShippingMethodSelection = function() {};

var ApplePaySession = {};
ApplePaySession.STATUS_SUCCESS = null;
ApplePaySession.STATUS_FAILURE = null;
ApplePaySession.STATUS_INVALID_SHIPPING_POSTAL_ADDRESS = null;
ApplePaySession.STATUS_INVALID_SHIPPING_CONTACT = null;

function _StripeApplePayResult() {}
_StripeApplePayResult.prototype.shippingContact = {};
_StripeApplePayResult.prototype.shippingMethod = {};
_StripeApplePayResult.prototype.token = {};
_StripeApplePayResult.prototype.token.card = null;
_StripeApplePayResult.prototype.token.id = null;


/* V3 */

var stripe = {};
/** @return {!_StripeElementFactory} */
stripe.elements = function() {};
/** @return {!_StripePromise} */
stripe.createToken = function(element, options) {};

var _StripeElementFactory = {};
/** @return {!_StripeElement} */
_StripeElementFactory.create = function(type, config) {};

var _StripeElement = {};
_StripeElement.mount = function (selector) {};

var _StripePromise = {};
_StripePromise.then = function(callback) {};
