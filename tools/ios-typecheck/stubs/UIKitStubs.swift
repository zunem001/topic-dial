// Stand-ins for the UIKit, Core Graphics and Core Animation API the files under test use, with Apple's names,
// labels and types, so the Linux compiler can type-check them. Bodies are empty: nothing here runs.
import Foundation

public typealias Selector = String
public func _selector(_ f: Any) -> Selector { return "" }

// MARK: Core Graphics

public struct CGAffineTransform: Equatable {
    public var a: CGFloat = 1, b: CGFloat = 0, c: CGFloat = 0, d: CGFloat = 1, tx: CGFloat = 0, ty: CGFloat = 0
    public init() {}
    public init(scaleX: CGFloat, y: CGFloat) {}
    public init(translationX: CGFloat, y: CGFloat) {}
    public init(rotationAngle: CGFloat) {}
    public static let identity = CGAffineTransform()
}

public final class CGColor {}
public final class CGColorSpace {}
public func CGColorSpaceCreateDeviceRGB() -> CGColorSpace { return CGColorSpace() }
public final class CGPath {}
public final class CGImage {}
public final class CGGradient {
    public init?(colorsSpace: CGColorSpace?, colors: CFArray, locations: UnsafePointer<CGFloat>?) {}
}
public enum CGLineCap { case butt, round, square }
public enum CGLineJoin { case miter, round, bevel }
public enum CGPathFillRule { case winding, evenOdd }
public enum CGBlendMode { case normal, copy, destinationOut }
public struct CGGradientDrawingOptions: OptionSet {
    public let rawValue: UInt32
    public init(rawValue: UInt32) { self.rawValue = rawValue }
    public static let drawsBeforeStartLocation = CGGradientDrawingOptions(rawValue: 1)
    public static let drawsAfterEndLocation = CGGradientDrawingOptions(rawValue: 2)
}
public typealias CFArray = NSArray

public final class CGContext {
    public func saveGState() {}
    public func restoreGState() {}
    public func addPath(_ path: CGPath) {}
    public func addRect(_ rect: CGRect) {}
    public func setFillColor(_ color: CGColor) {}
    public func setStrokeColor(_ color: CGColor) {}
    public func setLineWidth(_ width: CGFloat) {}
    public func setLineCap(_ cap: CGLineCap) {}
    public func setLineJoin(_ join: CGLineJoin) {}
    public func setBlendMode(_ mode: CGBlendMode) {}
    public func setShadow(offset: CGSize, blur: CGFloat, color: CGColor?) {}
    public func fillPath(using rule: CGPathFillRule = .winding) {}
    public func strokePath() {}
    public func fill(_ rect: CGRect) {}
    public func fillEllipse(in rect: CGRect) {}
    public func clear(_ rect: CGRect) {}
    public func clip(using rule: CGPathFillRule = .winding) {}
    public func clip(to rect: CGRect) {}
    public func replacePathWithStrokedPath() {}
    public func move(to point: CGPoint) {}
    public func addLine(to point: CGPoint) {}
    public func translateBy(x: CGFloat, y: CGFloat) {}
    public func scaleBy(x: CGFloat, y: CGFloat) {}
    public func drawLinearGradient(_ gradient: CGGradient, start: CGPoint, end: CGPoint, options: CGGradientDrawingOptions) {}
    public func drawRadialGradient(_ gradient: CGGradient, startCenter: CGPoint, startRadius: CGFloat, endCenter: CGPoint, endRadius: CGFloat, options: CGGradientDrawingOptions) {}
}

// MARK: Core Animation

public struct CATransform3D {
    public var m11: CGFloat = 1, m12: CGFloat = 0, m13: CGFloat = 0, m41: CGFloat = 0, m42: CGFloat = 0
}
public let CATransform3DIdentity = CATransform3D()
public func CATransform3DMakeScale(_ sx: CGFloat, _ sy: CGFloat, _ sz: CGFloat) -> CATransform3D { return CATransform3D() }
public func CATransform3DMakeTranslation(_ tx: CGFloat, _ ty: CGFloat, _ tz: CGFloat) -> CATransform3D { return CATransform3D() }
public func CATransform3DConcat(_ a: CATransform3D, _ b: CATransform3D) -> CATransform3D { return a }
public func CATransform3DEqualToTransform(_ a: CATransform3D, _ b: CATransform3D) -> Bool { return true }
public func CACurrentMediaTime() -> Double { return 0.0 }

public struct CAMediaTimingFunctionName: Hashable {
    public let rawValue: String
    public init(rawValue: String) { self.rawValue = rawValue }
    public static let linear = CAMediaTimingFunctionName(rawValue: "linear")
    public static let easeIn = CAMediaTimingFunctionName(rawValue: "easeIn")
    public static let easeOut = CAMediaTimingFunctionName(rawValue: "easeOut")
    public static let easeInEaseOut = CAMediaTimingFunctionName(rawValue: "easeInEaseOut")
}
public final class CAMediaTimingFunction {
    public init(name: CAMediaTimingFunctionName) {}
    public init(controlPoints c1x: Float, _ c1y: Float, _ c2x: Float, _ c2y: Float) {}
}
open class CAAnimation {
    public init() {}
    open var duration: Double = 0.0
    open var beginTime: Double = 0.0
    open var isRemovedOnCompletion: Bool = true
    open var timingFunction: CAMediaTimingFunction?
}
open class CAPropertyAnimation: CAAnimation {
    public init(keyPath: String?) {}
    open var isAdditive: Bool = false
}
open class CABasicAnimation: CAPropertyAnimation {
    open var fromValue: Any?
    open var toValue: Any?
}
open class CAKeyframeAnimation: CAPropertyAnimation {
    open var values: [Any]?
    open var keyTimes: [NSNumber]?
    open var timingFunctions: [CAMediaTimingFunction]?
}
open class CALayer {
    public init() {}
    open var cornerRadius: CGFloat = 0.0
    open var opacity: Float = 1.0
    open var frame: CGRect = .zero
    open var bounds: CGRect = .zero
    open var position: CGPoint = .zero
    open var transform: CATransform3D = CATransform3DIdentity
    open var sublayerTransform: CATransform3D = CATransform3DIdentity
    open var backgroundColor: CGColor?
    open func add(_ animation: CAAnimation, forKey key: String?) {}
    open func removeAnimation(forKey key: String) {}
    open func animation(forKey key: String) -> CAAnimation? { return nil }
    open func insertSublayer(_ layer: CALayer, at index: UInt32) {}
    open func removeFromSuperlayer() {}
    open func presentation() -> Self? { return nil }
}

// MARK: UIKit

public struct UIEdgeInsets: Equatable {
    public var top: CGFloat, left: CGFloat, bottom: CGFloat, right: CGFloat
    public init() { self.top = 0; self.left = 0; self.bottom = 0; self.right = 0 }
    public init(top: CGFloat, left: CGFloat, bottom: CGFloat, right: CGFloat) { self.top = top; self.left = left; self.bottom = bottom; self.right = right }
}

open class UIColor {
    public init(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat) {}
    public init(white: CGFloat, alpha: CGFloat) {}
    open var cgColor: CGColor { return CGColor() }
    open func withAlphaComponent(_ alpha: CGFloat) -> UIColor { return self }
    public static var clear: UIColor { return UIColor(white: 0, alpha: 0) }
    public static var black: UIColor { return UIColor(white: 0, alpha: 1) }
    public static var white: UIColor { return UIColor(white: 1, alpha: 1) }
    public static var gray: UIColor { return UIColor(white: 0.5, alpha: 1) }
}

open class UIFont {
    public init() {}
    public static func systemFont(ofSize size: CGFloat) -> UIFont { return UIFont() }
}

public enum NSTextAlignment { case left, center, right, natural, justified }
public enum NSLineBreakMode { case byWordWrapping, byCharWrapping, byClipping, byTruncatingHead, byTruncatingTail, byTruncatingMiddle }

extension NSAttributedString.Key {
    public static let font = NSAttributedString.Key(rawValue: "NSFont")
    public static let foregroundColor = NSAttributedString.Key(rawValue: "NSColor")
}

extension NSString {
    public func size(withAttributes attrs: [NSAttributedString.Key: Any]?) -> CGSize { return .zero }
}

open class UIImage {
    public init() {}
    open var size: CGSize { return .zero }
    public enum RenderingMode { case automatic, alwaysOriginal, alwaysTemplate }
    public enum ResizingMode { case tile, stretch }
    open func stretchableImage(withLeftCapWidth leftCapWidth: Int, topCapHeight: Int) -> UIImage { return self }
    open func resizableImage(withCapInsets capInsets: UIEdgeInsets, resizingMode: UIImage.ResizingMode) -> UIImage { return self }
    open func withRenderingMode(_ renderingMode: UIImage.RenderingMode) -> UIImage { return self }
}

public final class UIBezierPath {
    public init(roundedRect rect: CGRect, cornerRadius: CGFloat) {}
    public var cgPath: CGPath { return CGPath() }
}

public func UIGraphicsGetCurrentContext() -> CGContext? { return nil }

public struct UIAccessibilityTraits: OptionSet {
    public let rawValue: UInt64
    public init(rawValue: UInt64) { self.rawValue = rawValue }
    public static let button = UIAccessibilityTraits(rawValue: 1)
    public static let adjustable = UIAccessibilityTraits(rawValue: 2)
}

open class UITouch: Hashable {
    public static func ==(lhs: UITouch, rhs: UITouch) -> Bool { return lhs === rhs }
    public func hash(into hasher: inout Hasher) {}
}
open class UIEvent {}

open class UIGestureRecognizer {
    public enum State { case possible, began, changed, ended, cancelled, failed }
    public init(target: Any?, action: Selector?) {}
    open var state: UIGestureRecognizer.State = .possible
    open weak var delegate: UIGestureRecognizerDelegate?
    open var view: UIView? { return nil }
    open var isEnabled: Bool = true
    open func require(toFail otherGestureRecognizer: UIGestureRecognizer) {}
    open func location(in view: UIView?) -> CGPoint { return .zero }
}
open class UIPanGestureRecognizer: UIGestureRecognizer {
    open func translation(in view: UIView?) -> CGPoint { return .zero }
    open func velocity(in view: UIView?) -> CGPoint { return .zero }
}
open class UITapGestureRecognizer: UIGestureRecognizer {}
open class UILongPressGestureRecognizer: UIGestureRecognizer {
    open var minimumPressDuration: Double = 0.5
}
public protocol UIGestureRecognizerDelegate: AnyObject {
    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer) -> Bool
}
extension UIGestureRecognizerDelegate {
    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool { return true }
    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer) -> Bool { return false }
}

public final class UIWindow {}

open class UIView {
    public enum ContentMode { case scaleToFill, redraw, center }
    public struct AnimationOptions: OptionSet {
        public let rawValue: UInt
        public init(rawValue: UInt) { self.rawValue = rawValue }
        public static let curveEaseIn = AnimationOptions(rawValue: 1)
        public static let curveEaseOut = AnimationOptions(rawValue: 2)
        public static let curveEaseInOut = AnimationOptions(rawValue: 4)
        public static let beginFromCurrentState = AnimationOptions(rawValue: 8)
        public static let allowUserInteraction = AnimationOptions(rawValue: 16)
    }

    public init(frame: CGRect) {}
    public convenience init() { self.init(frame: .zero) }
    public required init?(coder: NSCoder) {}

    open var frame: CGRect = .zero
    open var bounds: CGRect = .zero
    open var center: CGPoint = .zero
    open var transform: CGAffineTransform = .identity
    open var alpha: CGFloat = 1.0
    open var isHidden: Bool = false
    open var clipsToBounds: Bool = false
    open var backgroundColor: UIColor?
    open var tintColor: UIColor!
    open var isOpaque: Bool = true
    open var contentMode: UIView.ContentMode = .scaleToFill
    open var isUserInteractionEnabled: Bool = true
    open var layer: CALayer { return CALayer() }
    open var superview: UIView? { return nil }
    open var subviews: [UIView] { return [] }
    open var window: UIWindow? { return nil }
    open var mask: UIView?

    open func addSubview(_ view: UIView) {}
    open func insertSubview(_ view: UIView, at index: Int) {}
    open func insertSubview(_ view: UIView, belowSubview siblingSubview: UIView) {}
    open func insertSubview(_ view: UIView, aboveSubview siblingSubview: UIView) {}
    open func bringSubviewToFront(_ view: UIView) {}
    open func removeFromSuperview() {}
    open func isDescendant(of view: UIView) -> Bool { return false }
    open func setNeedsDisplay() {}
    open func setNeedsLayout() {}
    open func layoutSubviews() {}
    open func didMoveToWindow() {}
    open func draw(_ rect: CGRect) {}
    open func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? { return nil }
    open func point(inside point: CGPoint, with event: UIEvent?) -> Bool { return false }
    open func convert(_ rect: CGRect, to view: UIView?) -> CGRect { return rect }
    open func sizeThatFits(_ size: CGSize) -> CGSize { return .zero }
    open func addGestureRecognizer(_ gestureRecognizer: UIGestureRecognizer) {}
    open func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool { return true }
    open func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {}
    open func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {}
    open func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {}

    open var isAccessibilityElement: Bool = false
    open var accessibilityTraits: UIAccessibilityTraits = []
    open var accessibilityLabel: String?
    open var accessibilityValue: String?
    open var accessibilityHint: String?
    open var accessibilityElementsHidden: Bool = false
    open func accessibilityIncrement() {}
    open func accessibilityDecrement() {}
    open func accessibilityActivate() -> Bool { return false }

    open class func animate(withDuration duration: Double, delay: Double, options: UIView.AnimationOptions = [], animations: @escaping () -> Void, completion: ((Bool) -> Void)? = nil) {}
}

open class UILabel: UIView {
    open var text: String?
    open var attributedText: NSAttributedString?
    open var font: UIFont!
    open var textColor: UIColor!
    open var textAlignment: NSTextAlignment = .natural
    open var lineBreakMode: NSLineBreakMode = .byTruncatingTail
    open var numberOfLines: Int = 1
    open var adjustsFontSizeToFitWidth: Bool = false
    open var minimumScaleFactor: CGFloat = 0.0
}

open class UIImageView: UIView {
    open var image: UIImage?
    public init(image: UIImage?) { super.init(frame: .zero) }
    public override init(frame: CGRect) { super.init(frame: frame) }
    public required init?(coder: NSCoder) { super.init(coder: coder) }
}

open class UIControl: UIView {
    public struct Event: OptionSet {
        public let rawValue: UInt
        public init(rawValue: UInt) { self.rawValue = rawValue }
        public static let touchUpInside = Event(rawValue: 1)
    }
    open func addTarget(_ target: Any?, action: Selector, for controlEvents: UIControl.Event) {}
}
open class UIButton: UIControl {}

// Linux Foundation lacks the empty initializer NSObject gives it on Apple platforms.
extension NSMutableAttributedString {
    public convenience init() {
        self.init(string: "")
    }
}
