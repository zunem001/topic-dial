// Stand-ins for the Telegram iOS API the files under test use, with the signatures copied from the real sources
// (ComponentFlow, Display, GlassBackgroundComponent, TelegramPresentationData, AccountContext, TelegramCore,
// ChatListUI, EmojiStatusComponent, AvatarNode, AppBundle, ChatSideTopicsPanel). Bodies are empty.
import Foundation

extension UIColor: Equatable {
    public static func ==(lhs: UIColor, rhs: UIColor) -> Bool { return lhs === rhs }
}
extension UIImage: Equatable {
    public static func ==(lhs: UIImage, rhs: UIImage) -> Bool { return lhs === rhs }
}

// MARK: ComponentFlow

public final class Empty: Equatable {
    public static func ==(lhs: Empty, rhs: Empty) -> Bool { return true }
}

public class _Environment {}

@dynamicMemberLookup
public final class EnvironmentValue<T: Equatable>: Equatable {
    public var value: T { fatalError() }
    public static func ==(lhs: EnvironmentValue<T>, rhs: EnvironmentValue<T>) -> Bool { return true }
    public subscript<V>(dynamicMember keyPath: KeyPath<T, V>) -> V { return self.value[keyPath: keyPath] }
}

public class Environment<T>: _Environment {
    public init(_ file: StaticString = #file, _ line: Int = #line) {}
}

public extension Environment {
    subscript(_ t1: T.Type) -> EnvironmentValue<T> where T: Equatable { fatalError() }
}

@resultBuilder
public struct EnvironmentBuilder {
    public struct Partial<T: Equatable> {}
    public static func buildBlock() -> Environment<Empty> { fatalError() }
    public static func buildExpression<T: Equatable>(_ expression: T) -> Partial<T> { fatalError() }
    public static func buildBlock<T1: Equatable>(_ t1: Partial<T1>) -> Environment<T1> { fatalError() }
}

open class ComponentState {
    open var _updated: ((ComponentTransition, Bool) -> Void)?
    public init() {}
    public final func updated(transition: ComponentTransition = .immediate, isLocal: Bool = false) {}
}
public final class EmptyComponentState: ComponentState {}

public protocol _TypeErasedComponent {}

public protocol Component: _TypeErasedComponent, Equatable {
    associatedtype EnvironmentType = Empty
    associatedtype View: UIView = UIView
    associatedtype State: ComponentState = EmptyComponentState

    func makeView() -> View
    func makeState() -> State
    func update(view: View, availableSize: CGSize, state: State, environment: Environment<EnvironmentType>, transition: ComponentTransition) -> CGSize
}

public extension Component where Self.State == EmptyComponentState {
    func makeState() -> State { return EmptyComponentState() }
}

public class AnyComponent<EnvironmentType>: _TypeErasedComponent, Equatable {
    public init<ComponentType: Component>(_ component: ComponentType) where ComponentType.EnvironmentType == EnvironmentType {}
    public static func ==(lhs: AnyComponent<EnvironmentType>, rhs: AnyComponent<EnvironmentType>) -> Bool { return true }
}

public final class ComponentView<EnvironmentType> {
    public private(set) var view: UIView?
    public weak var parentState: ComponentState?
    public init() {}
    public func update(transition: ComponentTransition, component: AnyComponent<EnvironmentType>, @EnvironmentBuilder environment: () -> Environment<EnvironmentType>, forceUpdate: Bool = false, containerSize: CGSize) -> CGSize { return .zero }
}

public struct ComponentTransition {
    public enum Animation {
        public enum Curve {
            case easeInOut
            case easeIn
            case spring
            case linear
            case custom(Float, Float, Float, Float)
            case bounce(stiffness: CGFloat, damping: CGFloat)
        }
        case none
        case curve(duration: Double, curve: Curve)
    }

    public var animation: Animation

    public init(animation: Animation) { self.animation = animation }
    public static var immediate: ComponentTransition = ComponentTransition(animation: .none)
    public static func easeInOut(duration: Double) -> ComponentTransition { return ComponentTransition(animation: .curve(duration: duration, curve: .easeInOut)) }
    public static func spring(duration: Double) -> ComponentTransition { return ComponentTransition(animation: .curve(duration: duration, curve: .spring)) }
    public func withAnimation(_ animation: Animation) -> ComponentTransition { return self }

    public func setFrame(view: UIView, frame: CGRect, completion: ((Bool) -> Void)? = nil) {}
    public func setFrame(layer: CALayer, frame: CGRect, completion: ((Bool) -> Void)? = nil) {}
    public func setPosition(view: UIView, position: CGPoint, delay: Double = 0.0, completion: ((Bool) -> Void)? = nil) {}
    public func setPosition(layer: CALayer, position: CGPoint, completion: ((Bool) -> Void)? = nil) {}
    public func setBounds(layer: CALayer, bounds: CGRect, completion: ((Bool) -> Void)? = nil) {}
    public func setAlpha(view: UIView, alpha: CGFloat, delay: Double = 0.0, completion: ((Bool) -> Void)? = nil) {}
    public func setAlpha(layer: CALayer, alpha: CGFloat, delay: Double = 0.0, completion: ((Bool) -> Void)? = nil) {}
    public func setScale(view: UIView, scale: CGFloat, delay: Double = 0.0, completion: ((Bool) -> Void)? = nil) {}
    public func setCornerRadius(layer: CALayer, cornerRadius: CGFloat, completion: ((Bool) -> Void)? = nil) {}
    public func animateAlpha(view: UIView, from fromValue: CGFloat, to toValue: CGFloat, delay: Double = 0.0, additive: Bool = false, completion: ((Bool) -> Void)? = nil) {}
    public func animateScale(view: UIView, from fromValue: CGFloat, to toValue: CGFloat, delay: Double = 0.0, additive: Bool = false, completion: ((Bool) -> Void)? = nil) {}
}

public extension ComponentTransition.Animation {
    var isImmediate: Bool {
        if case .none = self {
            return true
        } else {
            return false
        }
    }
}

// MARK: Display

public let UIScreenPixel: CGFloat = 1.0 / 3.0

public struct Font {
    public enum Design { case regular, serif, round, monospace, camera }
    public struct Traits: OptionSet {
        public let rawValue: Int32
        public init(rawValue: Int32) { self.rawValue = rawValue }
        public static let italic = Traits(rawValue: 1 << 0)
        public static let monospacedNumbers = Traits(rawValue: 1 << 1)
    }
    public enum Weight { case regular, thin, light, medium, semibold, bold, heavy }
    public enum Width { case standard, condensed, compressed, expanded }
    public static func with(size: CGFloat, design: Design = .regular, weight: Weight = .regular, width: Width = .standard, traits: Traits = []) -> UIFont { return UIFont() }
    public static func regular(_ size: CGFloat) -> UIFont { return UIFont() }
    public static func medium(_ size: CGFloat) -> UIFont { return UIFont() }
    public static func semibold(_ size: CGFloat) -> UIFont { return UIFont() }
    public static func bold(_ size: CGFloat) -> UIFont { return UIFont() }
}

public extension NSAttributedString {
    convenience init(string: String, font: UIFont? = nil, textColor: UIColor = UIColor.black, paragraphAlignment: NSTextAlignment? = nil) {
        self.init(string: string)
    }
}

public extension UIColor {
    func withMultipliedAlpha(_ alpha: CGFloat) -> UIColor { return self }
}

public func generateImage(_ size: CGSize, opaque: Bool = false, scale: CGFloat? = nil, rotatedContext: (CGSize, CGContext) -> Void) -> UIImage? { return nil }
public func generateStretchableFilledCircleImage(diameter: CGFloat, color: UIColor?, strokeColor: UIColor? = nil, strokeWidth: CGFloat? = nil, backgroundColor: UIColor? = nil) -> UIImage? { return nil }

public enum ImpactHapticFeedbackStyle: Hashable {
    case light, medium, heavy, soft, rigid, veryLight, click05, click06
}
public final class HapticFeedback {
    public init() {}
    public func prepareTap() {}
    public func tap() {}
    public func prepareImpact(_ style: ImpactHapticFeedbackStyle = .medium) {}
    public func impact(_ style: ImpactHapticFeedbackStyle = .medium, intensity: CGFloat? = nil) {}
}

public final class DisplayLinkAnimator {
    public init(duration: Double, from fromValue: CGFloat, to toValue: CGFloat, update: @escaping (CGFloat) -> Void, completion: @escaping () -> Void) {}
    public func invalidate() {}
}

public final class ConstantDisplayLinkAnimator {
    public var isPaused: Bool = true
    public init(update: @escaping () -> Void) {}
    public func invalidate() {}
}

open class HighlightTrackingButton: UIButton {
    public var highligthedChanged: (Bool) -> Void = { _ in }
}

open class ViewController {}

open class SimpleLayer: CALayer {}

// MARK: GlassBackgroundComponent

open class GlassBackgroundView: UIView {
    public struct TintColor: Equatable {
        public enum Kind: Equatable {
            case panel
            case clear
        }
        public init(kind: Kind, innerColor: UIColor? = nil, innerInset: CGFloat = 3.0) {}
    }
    public var contentView: UIView { return UIView() }
    public func update(size: CGSize, cornerRadius: CGFloat, isDark: Bool, tintColor: TintColor, isInteractive: Bool = false, isVisible: Bool = true, transition: ComponentTransition) {}
}

public final class GlassBackgroundContainerView: UIView {
    public var contentView: UIView { return UIView() }
    public func update(size: CGSize, isDark: Bool, transition: ComponentTransition) {}
}

// MARK: TelegramPresentationData

public final class PresentationThemeFillForeground {
    public let fillColor = UIColor.white
    public let foregroundColor = UIColor.black
}
public final class PresentationThemeList {
    public let itemPrimaryTextColor = UIColor.black
    public let plainBackgroundColor = UIColor.white
    public let mediaPlaceholderColor = UIColor.gray
    public let itemPlainSeparatorColor = UIColor.gray
    public let itemCheckColors = PresentationThemeFillForeground()
}
public final class PresentationThemeChatList {
    public let unreadBadgeActiveBackgroundColor = UIColor.white
    public let unreadBadgeActiveTextColor = UIColor.white
    public let unreadBadgeInactiveBackgroundColor = UIColor.white
    public let unreadBadgeInactiveTextColor = UIColor.white
}
public final class PresentationThemeChatInputPanel {
    public let panelControlAccentColor = UIColor.white
    public let panelControlColor = UIColor.gray
    public let primaryTextColor = UIColor.black
    public let secondaryTextColor = UIColor.gray
}
public final class PresentationThemeChat {
    public let inputPanel = PresentationThemeChatInputPanel()
}
public final class PresentationThemeRootNavigationBar {
    public let accentTextColor = UIColor.white
    public let controlColor = UIColor.white
}
public final class PresentationThemeRootController {
    public let navigationBar = PresentationThemeRootNavigationBar()
}
public final class PresentationTheme {
    public let overallDarkAppearance = false
    public let list = PresentationThemeList()
    public let chatList = PresentationThemeChatList()
    public let chat = PresentationThemeChat()
    public let rootController = PresentationThemeRootController()
}

public final class PresentationStrings {
    public var Chat_InlineTopicMenu_AllTab: String { return "" }
    public var Chat_InlineTopicMenu_TabAll: String { return "" }
    public var Chat_InlineTopicMenu_NewForumThreadTab: String { return "" }
    public var Conversation_EmptyPlaceholder: String { return "" }
    public var Conversation_typing: String { return "" }
    public final class FormattedString {
        public let string: String
        init(string: String) {
            self.string = string
        }
    }
    public func DialogList_SingleTypingSuffix(_ _0: String) -> FormattedString { return FormattedString(string: _0) }
    // Added by the patch to Localizable.strings.
    public var Chat_TopicsRoll_KeepOpen: String { return "" }
    public var Chat_TopicsRoll_WindUp: String { return "" }
    public var Chat_TopicsRoll_WheelHint: String { return "" }
    public var Chat_TopicsRoll_NoNews: String { return "" }
}

public struct PresentationPersonNameOrder {}
public struct PresentationDateTimeFormat {}
public final class PresentationData {
    public let strings = PresentationStrings()
    public let nameDisplayOrder = PresentationPersonNameOrder()
    public let dateTimeFormat = PresentationDateTimeFormat()
}

public func countString(_ count: Int64, forceDecimal: Bool = false) -> String { return "" }

public struct PresentationResourcesChatList {
    public static func generalTopicTemplateIcon(_ theme: PresentationTheme) -> UIImage? { return nil }
}

// MARK: SwiftSignalKit, AccountContext

public final class Atomic<T> {
    public func with<R>(_ f: (T) -> R) -> R { fatalError() }
}

public final class ContentSettings {}
public final class AnimationCache {}
public final class MultiAnimationRenderer {}
public final class TelegramMediaFile {}

public protocol SharedAccountContext: AnyObject {
    var currentPresentationData: Atomic<PresentationData> { get }
}

public final class Account {
    public var peerId: EnginePeer.Id { fatalError() }
}

public protocol AccountContext: AnyObject {
    var sharedContext: SharedAccountContext { get }
    var account: Account { get }
    var currentContentSettings: Atomic<ContentSettings> { get }
    var animationCache: AnimationCache { get }
    var animationRenderer: MultiAnimationRenderer { get }
}

public enum ChatControllerAnimateInnerChatSwitchDirection {
    case up
    case down
    case left
    case right
}

// MARK: TelegramCore

public enum EnginePeer: Equatable {
    public struct Id: Hashable {
        public init(_ value: Int64) {}
        public func toInt64() -> Int64 { return 0 }
    }
    case user
    case channel

    // LocalizedPeerData
    public var compactDisplayTitle: String { return "" }
}

public final class EngineRenderedPeer: Equatable {
    public var chatMainPeer: EnginePeer? { return nil }
    public static func ==(lhs: EngineRenderedPeer, rhs: EngineRenderedPeer) -> Bool { return true }
}

public final class EngineMessage: Equatable {
    public static func ==(lhs: EngineMessage, rhs: EngineMessage) -> Bool { return true }
}

public struct EnginePeerReadCounters: Equatable {
    public var count: Int32 { return 0 }
}

public final class EngineMessageHistoryThread {
    public final class Info: Equatable {
        public let title: String = ""
        public let icon: Int64? = nil
        public let iconColor: Int32 = 0
        public static func ==(lhs: Info, rhs: Info) -> Bool { return true }
    }
}

public struct MessageHistoryThreadData: Equatable {
    public var info: EngineMessageHistoryThread.Info
}

public final class EngineChatList {
    public final class Item: Equatable {
        public enum Id: Hashable {
            case chatList(EnginePeer.Id)
            case forum(Int64)
        }
        public enum PinnedIndex: Equatable {
            case none
            case index(Int)
        }
        public enum Index: Equatable {
            case chatList(Int)
            case forum(pinnedIndex: PinnedIndex, timestamp: Int32, threadId: Int64, namespace: Int32, id: Int32)
        }

        public var id: Id { fatalError() }
        public var index: Index { fatalError() }
        public var messages: [EngineMessage] { fatalError() }
        public var readCounters: EnginePeerReadCounters? { fatalError() }
        public var isMuted: Bool { fatalError() }
        public var threadData: MessageHistoryThreadData? { fatalError() }
        public var renderedPeer: EngineRenderedPeer { fatalError() }
        public var hasUnseenMentions: Bool { fatalError() }

        public static func ==(lhs: Item, rhs: Item) -> Bool { return true }
    }
}

// MARK: ChatListUI

public struct MessageTextEntity {}
public final class ChatTextInputTextCustomEmojiAttribute {}

public func chatListItemStrings(strings: PresentationStrings, nameDisplayOrder: PresentationPersonNameOrder, dateTimeFormat: PresentationDateTimeFormat, contentSettings: ContentSettings, messages: [EngineMessage], chatPeer: EngineRenderedPeer, accountPeerId: EnginePeer.Id, enableMediaEmoji: Bool = true, isPeerGroup: Bool = false) -> (peer: EnginePeer?, hideAuthor: Bool, messageText: String, messageEntities: [MessageTextEntity], spoilers: [NSRange]?, customEmojiRanges: [(NSRange, ChatTextInputTextCustomEmojiAttribute)]?, richTextPreview: NSAttributedString?) {
    fatalError()
}

// MARK: EmojiStatusComponent

public final class EmojiStatusComponent: Component {
    public typealias EnvironmentType = Empty

    public enum AnimationContent: Equatable {
        case customEmoji(fileId: Int64)
    }
    public enum LoopMode: Equatable {
        case forever
        case count(Int)
    }
    public enum Content: Equatable {
        case none
        case animation(content: AnimationContent, size: CGSize, placeholderColor: UIColor, themeColor: UIColor?, loopMode: LoopMode)
        case topic(title: String, color: Int32, size: CGSize)
        case image(image: UIImage?, tintColor: UIColor?)
    }

    public convenience init(
        context: AccountContext,
        animationCache: AnimationCache,
        animationRenderer: MultiAnimationRenderer,
        content: Content,
        particleColor: UIColor? = nil,
        size: CGSize? = nil,
        roundMask: Bool = false,
        isVisibleForAnimations: Bool,
        useSharedAnimation: Bool = false,
        action: (() -> Void)?,
        emojiFileUpdated: ((TelegramMediaFile?) -> Void)? = nil,
        tag: AnyObject? = nil
    ) {
        self.init()
    }
    private init() {}

    public static func ==(lhs: EmojiStatusComponent, rhs: EmojiStatusComponent) -> Bool { return true }

    public final class View: UIView {}
    public func makeView() -> View { return View(frame: .zero) }
    public func update(view: View, availableSize: CGSize, state: EmptyComponentState, environment: Environment<Empty>, transition: ComponentTransition) -> CGSize { return .zero }
}

// MARK: AvatarNode

public enum AvatarNodeClipStyle {
    case none
    case round
    case roundedRect
}
public final class AvatarNodeImageOverride {}
public final class MessageReference {}

public func avatarPlaceholderFont(size: CGFloat) -> UIFont { return UIFont() }

public final class AvatarNode {
    public var view: UIView { fatalError() }
    public var frame: CGRect = .zero
    public var isUserInteractionEnabled: Bool = true
    public init(font: UIFont) {}
    public func updateSize(size: CGSize) {}
    public func setPeer(
        context: AccountContext,
        account: Account? = nil,
        theme: PresentationTheme,
        peer: EnginePeer?,
        authorOfMessage: MessageReference? = nil,
        overrideImage: AvatarNodeImageOverride? = nil,
        emptyColor: UIColor? = nil,
        clipStyle: AvatarNodeClipStyle = .round,
        synchronousLoad: Bool = false,
        displayDimensions: CGSize = CGSize(width: 60.0, height: 60.0),
        storeUnrounded: Bool = false,
        cutoutRect: CGRect? = nil
    ) {}
}

// MARK: AppBundle

public extension UIImage {
    convenience init?(bundleImageName: String) {
        self.init()
    }
}

// MARK: ChatSideTopicsPanel (the parts of ChatSideTopicsPanel.swift the files under test call, as the patch leaves them)

public final class ChatSidePanelEnvironment: Equatable {
    public let insets: UIEdgeInsets
    public init(insets: UIEdgeInsets) { self.insets = insets }
    public static func ==(lhs: ChatSidePanelEnvironment, rhs: ChatSidePanelEnvironment) -> Bool { return lhs.insets == rhs.insets }
}

public final class ChatSideTopicsPanel: Component {
    public typealias EnvironmentType = ChatSidePanelEnvironment

    public enum Location {
        case side
        case top
        case bottom
    }
    public enum Kind: Equatable {
        case forum
        case monoforum
        case botForum(forumManagedByUser: Bool)
    }

    public init(
        context: AccountContext,
        theme: PresentationTheme,
        strings: PresentationStrings,
        location: Location,
        peerId: EnginePeer.Id,
        kind: Kind,
        topicId: Int64?,
        controller: @escaping () -> ViewController?,
        togglePanel: @escaping () -> Void,
        updateTopicId: @escaping (Int64?, Bool) -> Void,
        openDeletePeer: @escaping (Int64) -> Void,
        rollControl: ChatTopicsRollControlView.Mode? = nil,
        rollControlAction: (() -> Void)? = nil
    ) {}

    public static func ==(lhs: ChatSideTopicsPanel, rhs: ChatSideTopicsPanel) -> Bool { return true }

    public final class View: UIView {
        public var itemsUpdated: (([EngineChatList.Item]) -> Void)?
        public var items: [EngineChatList.Item] { return [] }
        public var typingUpdated: (([Int64: String]) -> Void)?
        public private(set) var typing: [Int64: String] = [:]
        public func topicIndex(threadId: Int64?) -> Int? { return nil }
    }
    public func makeView() -> View { return View(frame: .zero) }
    public func update(view: View, availableSize: CGSize, state: EmptyComponentState, environment: Environment<ChatSidePanelEnvironment>, transition: ComponentTransition) -> CGSize { return .zero }
}
