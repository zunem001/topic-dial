import Foundation
import UIKit
import Display
import ComponentFlow
import AccountContext
import TelegramCore
import TelegramPresentationData
import EmojiStatusComponent
import AvatarNode
import AppBundle
import ChatListUI

/// One topic as the wheel and its display show it.
public final class ChatTopicsRollItem: Equatable {
    public enum Icon: Equatable {
        case all
        case general
        case emoji(fileId: Int64)
        case topic(title: String, color: Int32)
        case peer(EnginePeer)
    }
    
    /// The thread (or, in a monoforum, the peer) this item opens; nil for All.
    public let id: Int64?
    public let title: String
    public let icon: Icon
    public let isPinned: Bool
    public let unreadCount: Int
    public let hasMention: Bool
    public let isMuted: Bool
    /// The latest message, as the topic list shows it: who wrote it and what it says.
    public let lastMessageAuthor: String?
    public let lastMessageText: String?
    /// Who is typing in the topic ("Ines is typing"), or nil.
    public let typingText: String?
    
    public init(id: Int64?, title: String, icon: Icon, isPinned: Bool, unreadCount: Int, hasMention: Bool, isMuted: Bool, lastMessageAuthor: String?, lastMessageText: String?, typingText: String? = nil) {
        self.id = id
        self.title = title
        self.icon = icon
        self.isPinned = isPinned
        self.unreadCount = unreadCount
        self.hasMention = hasMention
        self.isMuted = isMuted
        self.lastMessageAuthor = lastMessageAuthor
        self.lastMessageText = lastMessageText
        self.typingText = typingText
    }
    
    public var isAll: Bool {
        return self.id == nil
    }
    
    public static func ==(lhs: ChatTopicsRollItem, rhs: ChatTopicsRollItem) -> Bool {
        if lhs === rhs {
            return true
        }
        if lhs.id != rhs.id || lhs.title != rhs.title || lhs.icon != rhs.icon || lhs.isPinned != rhs.isPinned {
            return false
        }
        if lhs.unreadCount != rhs.unreadCount || lhs.hasMention != rhs.hasMention || lhs.isMuted != rhs.isMuted {
            return false
        }
        if lhs.lastMessageAuthor != rhs.lastMessageAuthor || lhs.lastMessageText != rhs.lastMessageText || lhs.typingText != rhs.typingText {
            return false
        }
        return true
    }
    
    /// The wheel's items in the bar's own order: All, the pinned topics, then the rest. `typing` names who is
    /// typing in each topic (an empty name when it is not known).
    public static func makeItems(context: AccountContext, kind: ChatSideTopicsPanel.Kind, rawItems: [EngineChatList.Item], typing: [Int64: String] = [:]) -> [ChatTopicsRollItem] {
        let presentationData = context.sharedContext.currentPresentationData.with { $0 }
        let strings = presentationData.strings
        
        let allTitle: String
        if case let .botForum(forumManagedByUser) = kind {
            allTitle = forumManagedByUser ? strings.Chat_InlineTopicMenu_NewForumThreadTab : strings.Chat_InlineTopicMenu_TabAll
        } else {
            allTitle = strings.Chat_InlineTopicMenu_AllTab
        }
        var result: [ChatTopicsRollItem] = [
            ChatTopicsRollItem(id: nil, title: allTitle, icon: .all, isPinned: false, unreadCount: 0, hasMention: false, isMuted: false, lastMessageAuthor: nil, lastMessageText: nil)
        ]
        
        let contentSettings = context.currentContentSettings.with { $0 }
        for item in rawItems {
            let id: Int64
            let title: String
            let icon: Icon
            switch item.id {
            case let .chatList(peerId):
                id = peerId.toInt64()
                if let peer = item.renderedPeer.chatMainPeer {
                    title = peer.compactDisplayTitle
                    icon = .peer(peer)
                } else {
                    title = " "
                    icon = .all
                }
            case let .forum(threadId):
                id = threadId
                if let threadData = item.threadData {
                    title = threadData.info.title
                    if threadId == 1 {
                        icon = .general
                    } else if let fileId = threadData.info.icon, fileId != 0 {
                        icon = .emoji(fileId: fileId)
                    } else {
                        icon = .topic(title: String(threadData.info.title.prefix(1)), color: threadData.info.iconColor)
                    }
                } else {
                    title = " "
                    icon = threadId == 1 ? .general : .topic(title: " ", color: 0)
                }
            }
            
            var isPinned = false
            if case let .forum(pinnedIndex, _, _, _, _) = item.index, case .index = pinnedIndex {
                isPinned = true
            }
            
            var lastMessageAuthor: String?
            var lastMessageText: String?
            if !item.messages.isEmpty {
                let (peer, hideAuthor, messageText, _, _, _, _) = chatListItemStrings(
                    strings: strings,
                    nameDisplayOrder: presentationData.nameDisplayOrder,
                    dateTimeFormat: presentationData.dateTimeFormat,
                    contentSettings: contentSettings,
                    messages: item.messages,
                    chatPeer: item.renderedPeer,
                    accountPeerId: context.account.peerId
                )
                if !hideAuthor, let peer {
                    lastMessageAuthor = peer.compactDisplayTitle
                }
                let text = messageText.replacingOccurrences(of: "\n", with: " ")
                lastMessageText = text.isEmpty ? nil : text
            }
            
            var typingText: String?
            if case .forum = item.id, let name = typing[id] {
                typingText = name.isEmpty ? strings.Conversation_typing : strings.DialogList_SingleTypingSuffix(name).string
            }
            
            result.append(ChatTopicsRollItem(
                id: id,
                title: title,
                icon: icon,
                isPinned: isPinned,
                unreadCount: Int(item.readCounters?.count ?? 0),
                hasMention: item.hasUnseenMentions,
                isMuted: item.isMuted,
                lastMessageAuthor: lastMessageAuthor,
                lastMessageText: lastMessageText,
                typingText: typingText
            ))
        }
        return result
    }
}

/// A view that draws itself with a closure, for the parts of the wheel that change with every frame it turns.
private final class ChatTopicsRollDrawingView: UIView {
    var draw: ((CGContext, CGRect) -> Void)?
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        
        self.isOpaque = false
        self.backgroundColor = .clear
        self.contentMode = .redraw
        self.isUserInteractionEnabled = false
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else {
            return
        }
        self.draw?(context, self.bounds)
    }
}

private func smoothstep(_ edge0: CGFloat, _ edge1: CGFloat, _ x: CGFloat) -> CGFloat {
    let t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)
}

private func overshoot(_ t: CGFloat, tension: CGFloat = 1.15) -> CGFloat {
    let x = t - 1.0
    return x * x * ((tension + 1.0) * x + tension) + 1.0
}

private func argb(_ value: UInt32) -> UIColor {
    return UIColor(red: CGFloat((value >> 16) & 0xff) / 255.0, green: CGFloat((value >> 8) & 0xff) / 255.0, blue: CGFloat(value & 0xff) / 255.0, alpha: CGFloat((value >> 24) & 0xff) / 255.0)
}

/// The drawing the wheel, the bar and the rail share: the well the wheel turns in, the glass over the topic you
/// are in, the drum's shaded ends and the lights on the scale.
public enum ChatTopicsRollDrawing {
    /// A well set into the glass: a little darker than the glass, shaded under its upper edge and lit along its
    /// lower one, like the slot of a dial.
    public static func drawWell(context: CGContext, rect: CGRect, cornerRadius: CGFloat, amount: CGFloat, isDark: Bool) {
        if amount <= 0.0 || rect.isEmpty {
            return
        }
        let path = UIBezierPath(roundedRect: rect, cornerRadius: cornerRadius).cgPath
        
        context.saveGState()
        context.addPath(path)
        context.setFillColor(UIColor(white: 0.0, alpha: (isDark ? 0.18 : 0.045) * amount).cgColor)
        context.fillPath()
        
        context.addPath(path)
        context.clip()
        
        let shadeColor = isDark ? argb(0x52000000) : argb(0x170F1E23)
        if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [shadeColor.withMultipliedAlpha(amount).cgColor, shadeColor.withAlphaComponent(0.0).cgColor] as CFArray, locations: [0.0, 1.0]) {
            context.drawLinearGradient(gradient, start: CGPoint(x: rect.minX, y: rect.minY), end: CGPoint(x: rect.minX, y: rect.minY + 3.5), options: [])
        }
        
        // The lit lower lip: the strip of the well below its own outline moved up by a point.
        let lip = UIBezierPath(roundedRect: rect.offsetBy(dx: 0.0, dy: -1.0), cornerRadius: cornerRadius).cgPath
        context.addRect(rect)
        context.addPath(lip)
        context.setFillColor(UIColor(white: 1.0, alpha: (isDark ? 0.05 : 0.7) * amount).cgColor)
        context.fillPath(using: .evenOdd)
        context.restoreGState()
    }
    
    /// A puck of white glass over the topic you are in, a little brighter at the top, with a soft shadow under it
    /// and a fine edge (frosted grey in a dark theme). The wheel's bead is a round one.
    public static func drawPuck(context: CGContext, rect: CGRect, cornerRadius: CGFloat, alpha: CGFloat, isDark: Bool) {
        if alpha <= 0.0 || rect.isEmpty {
            return
        }
        context.saveGState()
        
        let shade = isDark ? argb(0x73000000) : argb(0x2B102824)
        for i in stride(from: 2, through: 1, by: -1) {
            let grow = 0.5 * CGFloat(i - 1)
            let shadowRect = rect.insetBy(dx: -grow, dy: -grow).offsetBy(dx: 0.0, dy: 0.5 * CGFloat(i))
            context.addPath(UIBezierPath(roundedRect: shadowRect, cornerRadius: cornerRadius + grow).cgPath)
            context.setFillColor(shade.withMultipliedAlpha(alpha * (i == 2 ? 0.35 : 0.6)).cgColor)
            context.fillPath()
        }
        
        let path = UIBezierPath(roundedRect: rect, cornerRadius: cornerRadius).cgPath
        let top = isDark ? argb(0x2BFFFFFF) : argb(0xFAFFFFFF)
        let bottom = isDark ? argb(0x17FFFFFF) : argb(0xF0F9FAFB)
        context.saveGState()
        context.addPath(path)
        context.clip()
        if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [top.withMultipliedAlpha(alpha).cgColor, bottom.withMultipliedAlpha(alpha).cgColor] as CFArray, locations: [0.0, 1.0]) {
            context.drawLinearGradient(gradient, start: CGPoint(x: rect.minX, y: rect.minY), end: CGPoint(x: rect.minX, y: rect.maxY), options: [])
        }
        context.restoreGState()
        
        context.addPath(path)
        context.setStrokeColor((isDark ? argb(0x0FFFFFFF) : argb(0x120F1E23)).withMultipliedAlpha(alpha).cgColor)
        context.setLineWidth(max(UIScreenPixel, 0.5))
        context.strokePath()
        
        // A line of light along its upper edge, inside.
        let inset: CGFloat = 1.0
        let innerRect = rect.insetBy(dx: inset, dy: inset)
        if !innerRect.isEmpty {
            let innerPath = UIBezierPath(roundedRect: innerRect, cornerRadius: max(0.0, cornerRadius - inset)).cgPath
            context.saveGState()
            context.addPath(innerPath)
            context.setLineWidth(1.0)
            context.replacePathWithStrokedPath()
            context.clip()
            let shine = isDark ? argb(0x24FFFFFF) : argb(0xE6FFFFFF)
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [shine.withMultipliedAlpha(alpha).cgColor, shine.withAlphaComponent(0.0).cgColor] as CFArray, locations: [0.0, 0.5]) {
                context.drawLinearGradient(gradient, start: CGPoint(x: rect.minX, y: rect.minY), end: CGPoint(x: rect.minX, y: rect.maxY), options: [])
            }
            context.restoreGState()
        }
        
        context.restoreGState()
    }
    
    /// The shade at the ends of a drum, where it curves away.
    public static func drawEnds(context: CGContext, rect: CGRect, cornerRadius: CGFloat, length: CGFloat, start: CGFloat, end: CGFloat, isVertical: Bool, isDark: Bool) {
        let color = isDark ? argb(0x52000000) : argb(0x21142832)
        context.saveGState()
        context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: cornerRadius).cgPath)
        context.clip()
        for i in 0 ..< 2 {
            let amount = i == 0 ? start : end
            if amount <= 0.0 {
                continue
            }
            guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [color.withMultipliedAlpha(amount).cgColor, color.withAlphaComponent(0.0).cgColor] as CFArray, locations: [0.0, 1.0]) else {
                continue
            }
            let startPoint: CGPoint
            let endPoint: CGPoint
            if isVertical {
                startPoint = CGPoint(x: rect.midX, y: i == 0 ? rect.minY : rect.maxY)
                endPoint = CGPoint(x: rect.midX, y: i == 0 ? rect.minY + length : rect.maxY - length)
            } else {
                startPoint = CGPoint(x: i == 0 ? rect.minX : rect.maxX, y: rect.midY)
                endPoint = CGPoint(x: i == 0 ? rect.minX + length : rect.maxX - length, y: rect.midY)
            }
            context.drawLinearGradient(gradient, start: startPoint, end: endPoint, options: [])
        }
        context.restoreGState()
    }
    
    /// A light on a scale, like a station on a radio dial: a short bar in the counter's colour, wider for a
    /// mention, grey and without its halo when the news is muted. Where someone is typing, it is three dots that
    /// rise and fall one after another (the caller keeps redrawing while it shows). `squeeze` narrows it as a drum
    /// turns it away.
    public static func drawLight(context: CGContext, center: CGPoint, squeeze: CGFloat, alpha: CGFloat, isTyping: Bool, isMention: Bool, isMuted: Bool, isVertical: Bool, theme: PresentationTheme) {
        if alpha <= 0.0 {
            return
        }
        if isTyping {
            let color = theme.chatList.unreadBadgeActiveBackgroundColor
            let time = CGFloat(CACurrentMediaTime())
            for k in 0 ..< 3 {
                var phase = (time * 1.6 - CGFloat(k) * 0.2).truncatingRemainder(dividingBy: 1.0)
                if phase < 0.0 {
                    phase += 1.0
                }
                let s = sin(.pi * phase)
                let pulse = alpha * (0.35 + 0.65 * s * s)
                let offset = CGFloat(k - 1) * 5.0 * squeeze
                let dot = isVertical ? CGPoint(x: center.x, y: center.y + offset) : CGPoint(x: center.x + offset, y: center.y)
                context.setFillColor(color.withMultipliedAlpha(pulse * 0.25).cgColor)
                context.fillEllipse(in: CGRect(x: dot.x - 2.75, y: dot.y - 2.75, width: 5.5, height: 5.5))
                context.setFillColor(color.withMultipliedAlpha(pulse).cgColor)
                context.fillEllipse(in: CGRect(x: dot.x - 1.5, y: dot.y - 1.5, width: 3.0, height: 3.0))
            }
            return
        }
        let color = isMuted ? theme.chatList.unreadBadgeInactiveBackgroundColor : theme.chatList.unreadBadgeActiveBackgroundColor
        let halfLength = (isMention ? 6.5 : 4.5) * squeeze
        let halfThickness: CGFloat = 1.5
        func lightRect(_ grow: CGFloat) -> CGRect {
            if isVertical {
                return CGRect(x: center.x - halfThickness - grow, y: center.y - halfLength - grow, width: (halfThickness + grow) * 2.0, height: (halfLength + grow) * 2.0)
            } else {
                return CGRect(x: center.x - halfLength - grow, y: center.y - halfThickness - grow, width: (halfLength + grow) * 2.0, height: (halfThickness + grow) * 2.0)
            }
        }
        if !isMuted {
            for k in stride(from: 2, through: 1, by: -1) {
                let halo = 1.5 * CGFloat(k)
                context.addPath(UIBezierPath(roundedRect: lightRect(halo), cornerRadius: halfThickness + halo).cgPath)
                context.setFillColor(color.withMultipliedAlpha(alpha * (k == 2 ? 0.12 : 0.24)).cgColor)
                context.fillPath()
            }
        }
        context.addPath(UIBezierPath(roundedRect: lightRect(0.0), cornerRadius: halfThickness).cgPath)
        context.setFillColor(color.withMultipliedAlpha(alpha).cgColor)
        context.fillPath()
    }
    
    /// The track a bar's tabs lie on: the wheel's well opened out along the bar, with the drum's shaded ends where
    /// the toggle and `«` sit. It stretches along the bar (down the rail, standing up).
    public static func generateTrackImage(theme: PresentationTheme, cornerRadius: CGFloat, isVertical: Bool) -> UIImage? {
        let endLength: CGFloat = 20.0
        let capX = isVertical ? cornerRadius : max(cornerRadius, endLength)
        let capY = isVertical ? max(cornerRadius, endLength) : cornerRadius
        let isDark = theme.overallDarkAppearance
        return generateImage(CGSize(width: capX * 2.0 + 1.0, height: capY * 2.0 + 1.0), rotatedContext: { size, context in
            let rect = CGRect(origin: CGPoint(), size: size)
            context.clear(rect)
            drawWell(context: context, rect: rect, cornerRadius: cornerRadius, amount: 1.0, isDark: isDark)
            drawEnds(context: context, rect: rect, cornerRadius: cornerRadius, length: endLength, start: 1.0, end: 1.0, isVertical: isVertical, isDark: isDark)
        })?.resizableImage(withCapInsets: UIEdgeInsets(top: capY, left: capX, bottom: capY, right: capX), resizingMode: .stretch)
    }
    
    public static func knurlColor(theme: PresentationTheme) -> UIColor {
        return theme.list.itemPrimaryTextColor.withMultipliedAlpha(theme.overallDarkAppearance ? 0.22 : 0.28)
    }
    
    /// The gear line: the wheel's knurled ticks, every 17 points along a track's lower edge (its right edge when it
    /// stands up). It shows only while a bar winds up or rolls out.
    public static func generateGearLineImage(theme: PresentationTheme, isVertical: Bool) -> UIImage? {
        let spacing: CGFloat = 17.0
        let size = isVertical ? CGSize(width: 3.5, height: spacing) : CGSize(width: spacing, height: 3.5)
        return generateImage(size, rotatedContext: { size, context in
            context.clear(CGRect(origin: CGPoint(), size: size))
            context.setStrokeColor(knurlColor(theme: theme).cgColor)
            context.setLineWidth(1.25)
            context.setLineCap(.round)
            if isVertical {
                context.move(to: CGPoint(x: 0.6, y: size.height * 0.5))
                context.addLine(to: CGPoint(x: size.width - 0.6, y: size.height * 0.5))
            } else {
                context.move(to: CGPoint(x: size.width * 0.5, y: 0.6))
                context.addLine(to: CGPoint(x: size.width * 0.5, y: size.height - 0.6))
            }
            context.strokePath()
        })
    }
}

/// The topic bar rolled up into a wheel. Topic icons sit on a drum in the bar's own order (All, the pinned topics,
/// then the rest), and the one in the middle, under the bead, is the topic you are in. Swipe it and it turns,
/// clicking at every topic and a little firmer where the pinned topics end; let go and it settles on a topic and
/// opens it. Tap it and the bar rolls back out. A topic with news lights up on the wheel's scale, like a station
/// on a radio dial, and where someone is typing its light is three dots that rise and fall; news further along
/// than the wheel shows makes that end of the well glow; a badge on the well's rim counts unread messages in the
/// other topics; and the wheel nudges toward a topic that just got a message. For side tabs it stands up
/// (`isVertical`).
///
/// The view's bounds are the well the drum turns in (108 × 36 points, or 36 × 108 standing up). The badge rides
/// the well's rim, outside the bounds, so the view does not clip.
public final class ChatTopicsRollWheelView: UIView, UIGestureRecognizerDelegate {
    public static let length: CGFloat = 108.0
    public static let thickness: CGFloat = 36.0
    
    private static let radius: CGFloat = 46.0
    private static let cell: CGFloat = 34.0
    private static let iconSize: CGFloat = 22.0
    private static let knurlFromEdge: CGFloat = 3.0
    private static let knurl: CGFloat = 3.5
    private static let lightFromEdge: CGFloat = 4.75
    private static var step: CGFloat {
        return cell / radius
    }
    
    /// A tap: roll the bar back out.
    public var tapped: (() -> Void)?
    /// The wheel settled on another topic after a swipe. Direction is +1 for later topics, -1 for earlier ones.
    public var turned: ((ChatTopicsRollItem, Int) -> Void)?
    /// While it turns, the topic under the lens and the way it is turning; nil once it has settled.
    public var scrubbed: ((ChatTopicsRollItem?, Int) -> Void)?
    /// A long press on the wheel.
    public var longPressed: (() -> Void)?
    
    private final class IconSlot {
        let key: String
        var icon: ComponentView<Empty>?
        var avatarNode: AvatarNode?
        let containerView: UIView
        
        init(key: String) {
            self.key = key
            self.containerView = UIView()
            self.containerView.isUserInteractionEnabled = false
        }
    }
    
    private let drumView: UIView
    private let underlayView: ChatTopicsRollDrawingView
    private let iconsContainer: UIView
    private let overlayView: ChatTopicsRollDrawingView
    private let badgeView: UIView
    private let badgeBackgroundView: UIImageView
    private let badgeLabel: UILabel
    
    private var context: AccountContext?
    private var theme: PresentationTheme?
    private var strings: PresentationStrings?
    
    public private(set) var items: [ChatTopicsRollItem] = []
    private var indexById: [Int64: Int] = [:]
    private var currentId: Int64?
    private var slots: [String: IconSlot] = [:]
    
    public private(set) var isVertical: Bool = false
    /// Whether the wheel draws its own well (the bar's track carries it while the bar is rolled out).
    public var drawsWell: Bool = true {
        didSet {
            if self.drawsWell != oldValue {
                self.underlayView.setNeedsDisplay()
            }
        }
    }
    
    private var position: CGFloat = 0.0
    private var beltOverride: CGFloat?
    private var lensAlpha: CGFloat = 1.0
    private var lastTick: Int = Int.min
    private var settleAnimator: DisplayLinkAnimator?
    
    private var nudgeStart: Double?
    private var nudgeDirection: CGFloat = 0.0
    private var nudgeAnimator: ConstantDisplayLinkAnimator?
    private var typingAnimator: ConstantDisplayLinkAnimator?
    
    private var hasBadge = false
    private var badgeIsMuted = false
    private var badgeCount = 0
    private var badgeHasMention = false
    
    private var panRecognizer: UIPanGestureRecognizer?
    private var dragFrom: CGFloat = 0.0
    private var isDragging = false
    private let hapticFeedback = HapticFeedback()
    
    public var isTurning: Bool {
        return self.isDragging || self.settleAnimator != nil
    }
    
    public var currentIndex: Int {
        return self.index(of: self.currentId)
    }
    
    override public init(frame: CGRect) {
        self.drumView = UIView()
        self.drumView.clipsToBounds = true
        self.drumView.isUserInteractionEnabled = false
        
        self.underlayView = ChatTopicsRollDrawingView()
        self.iconsContainer = UIView()
        self.iconsContainer.isUserInteractionEnabled = false
        self.overlayView = ChatTopicsRollDrawingView()
        
        self.badgeView = UIView()
        self.badgeView.isUserInteractionEnabled = false
        self.badgeView.alpha = 0.0
        self.badgeBackgroundView = UIImageView()
        self.badgeLabel = UILabel()
        self.badgeLabel.textAlignment = .center
        self.badgeLabel.font = Font.with(size: 11.0, weight: .semibold, traits: [.monospacedNumbers])
        
        super.init(frame: frame)
        
        self.clipsToBounds = false
        
        self.addSubview(self.drumView)
        self.drumView.addSubview(self.underlayView)
        self.drumView.addSubview(self.iconsContainer)
        self.drumView.addSubview(self.overlayView)
        
        self.badgeView.addSubview(self.badgeBackgroundView)
        self.badgeView.addSubview(self.badgeLabel)
        self.addSubview(self.badgeView)
        
        self.underlayView.draw = { [weak self] context, rect in
            self?.drawUnderlay(context: context, rect: rect)
        }
        self.overlayView.draw = { [weak self] context, rect in
            self?.drawOverlay(context: context, rect: rect)
        }
        
        let panRecognizer = UIPanGestureRecognizer(target: self, action: #selector(self.panGesture(_:)))
        panRecognizer.delegate = self
        self.panRecognizer = panRecognizer
        self.addGestureRecognizer(panRecognizer)
        
        let tapRecognizer = UITapGestureRecognizer(target: self, action: #selector(self.tapGesture(_:)))
        self.addGestureRecognizer(tapRecognizer)
        
        let longPressRecognizer = UILongPressGestureRecognizer(target: self, action: #selector(self.longPressGesture(_:)))
        longPressRecognizer.minimumPressDuration = 0.45
        self.addGestureRecognizer(longPressRecognizer)
        tapRecognizer.require(toFail: longPressRecognizer)
        
        self.isAccessibilityElement = true
        self.accessibilityTraits = [.button, .adjustable]
    }
    
    required public init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    deinit {
        self.settleAnimator?.invalidate()
        self.nudgeAnimator?.invalidate()
        self.typingAnimator?.invalidate()
    }
    
    override public func didMoveToWindow() {
        super.didMoveToWindow()
        
        self.updateTypingAnimation()
    }
    
    // MARK: Content
    
    public func update(context: AccountContext, theme: PresentationTheme, strings: PresentationStrings, items: [ChatTopicsRollItem], currentId: Int64?, isVertical: Bool, animated: Bool) {
        let themeUpdated = self.theme !== theme
        self.context = context
        self.theme = theme
        self.strings = strings
        
        var previousUnread: [Int64: Int] = [:]
        for item in self.items {
            if let id = item.id {
                previousUnread[id] = item.unreadCount
            }
        }
        
        let itemsUpdated = self.items != items
        self.items = items
        self.indexById.removeAll()
        for i in 0 ..< items.count {
            if let id = items[i].id {
                self.indexById[id] = i
            }
        }
        
        if self.isVertical != isVertical {
            self.isVertical = isVertical
            self.setNeedsLayout()
        }
        
        let isIdle = !self.isDragging && self.settleAnimator == nil && self.beltOverride == nil
        let changed = self.currentId != currentId
        self.currentId = currentId
        if isIdle {
            let index = CGFloat(self.index(of: currentId))
            if changed && animated && self.window != nil && self.alpha > 0.0 && abs(index - self.position) > 0.01 {
                // Picked somewhere else (a message, a link, the bar): the wheel turns there on its own.
                self.animatePosition(to: Int(index), fromSwipe: false)
            } else {
                self.position = index
                self.lastTick = Int(index)
            }
        }
        
        // A topic that just got a message, other than this one and not muted, gives the wheel a nudge.
        if animated && isIdle && !changed {
            for i in 0 ..< items.count {
                let item = items[i]
                guard let id = item.id, let previous = previousUnread[id] else {
                    continue
                }
                if item.unreadCount > previous && !item.isMuted && id != currentId {
                    let direction = i - Int(self.position.rounded())
                    if direction != 0 {
                        self.startNudge(direction: direction > 0 ? 1.0 : -1.0)
                    }
                    break
                }
            }
        }
        
        self.updateBadge(animated: animated)
        if themeUpdated || itemsUpdated {
            self.updateIconContents(force: themeUpdated)
        }
        self.updateAccessibility()
        self.updateTypingAnimation()
        self.updateDrum()
    }
    
    public func index(of id: Int64?) -> Int {
        guard let id else {
            return 0
        }
        return self.indexById[id] ?? 0
    }
    
    public func item(at index: Int) -> ChatTopicsRollItem? {
        if index >= 0 && index < self.items.count {
            return self.items[index]
        }
        return nil
    }
    
    /// While the bar rolls in or out, the wheel turns to the tab it is passing.
    public func setBeltOverride(_ value: CGFloat?) {
        if self.beltOverride != value {
            self.beltOverride = value
            self.updateDrum()
        }
    }
    
    /// The bead: it stays on your topic as the bar rolls out, so the bar's puck takes over from it.
    public func setLensAlpha(_ value: CGFloat) {
        if self.lensAlpha != value {
            self.lensAlpha = value
            self.underlayView.setNeedsDisplay()
        }
    }
    
    // MARK: Turning
    
    private func animatePosition(to target: Int, fromSwipe: Bool) {
        self.settleAnimator?.invalidate()
        let from = self.position
        let to = CGFloat(target)
        self.settleAnimator = DisplayLinkAnimator(duration: 0.42, from: 0.0, to: 1.0, update: { [weak self] t in
            guard let self else {
                return
            }
            self.position = from + (to - from) * overshoot(t)
            if fromSwipe {
                self.checkTick()
            }
            self.updateDrum()
        }, completion: { [weak self] in
            guard let self else {
                return
            }
            self.settleAnimator = nil
            self.position = to
            self.lastTick = target
            self.evictSlots()
            self.updateDrum()
            if fromSwipe {
                self.scrubbed?(nil, 0)
            }
        })
    }
    
    // One click for every topic that passes the lens, a firmer one where the pinned topics end.
    private func checkTick() {
        if self.items.isEmpty {
            return
        }
        let index = max(0, min(self.items.count - 1, Int(self.position.rounded())))
        if index == self.lastTick {
            return
        }
        let previous = self.lastTick
        let isEdge = previous >= 0 && previous < self.items.count && self.items[previous].isPinned != self.items[index].isPinned
        self.lastTick = index
        if isEdge {
            self.hapticFeedback.impact(.medium)
        } else {
            self.hapticFeedback.tap()
        }
        self.scrubbed?(self.items[index], previous == Int.min ? 0 : (index > previous ? 1 : -1))
    }
    
    /// Turns back to a topic without opening it, when the chat could not open the one the wheel turned to.
    public func turn(to id: Int64?) {
        self.currentId = id
        self.updateBadge(animated: true)
        self.updateAccessibility()
        self.updateTypingAnimation()
        self.animatePosition(to: self.index(of: id), fromSwipe: false)
    }
    
    /// Turns to the next (+1) or previous (-1) topic and opens it, as a swipe would.
    @discardableResult
    public func turn(by direction: Int) -> Bool {
        if self.items.isEmpty || self.isDragging {
            return false
        }
        let from = self.index(of: self.currentId)
        let target = max(0, min(self.items.count - 1, from + direction))
        if target == from {
            return false
        }
        self.lastTick = Int(self.position.rounded())
        self.finishTurn(from: from, target: target)
        return true
    }
    
    private func finishTurn(from: Int, target: Int) {
        self.animatePosition(to: target, fromSwipe: true)
        if target != from {
            let item = self.items[target]
            self.currentId = item.id
            self.updateBadge(animated: true)
            self.updateAccessibility()
            self.updateTypingAnimation()
            self.turned?(item, target > from ? 1 : -1)
        } else {
            self.scrubbed?(nil, 0)
        }
    }
    
    private func rubberBand(_ p: CGFloat) -> CGFloat {
        let last = CGFloat(self.items.count - 1)
        if p < 0.0 {
            return -0.45 * (1.0 - exp(p / 0.9))
        }
        if p > last {
            return last + 0.45 * (1.0 - exp(-(p - last) / 0.9))
        }
        return p
    }
    
    override public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        if gestureRecognizer === self.panRecognizer, let panRecognizer = self.panRecognizer {
            if self.items.count < 2 {
                return false
            }
            // The wheel turns along its axis, so it keeps a swipe along it for itself (no swipe back from here).
            let velocity = panRecognizer.velocity(in: self)
            if self.isVertical {
                return abs(velocity.y) > abs(velocity.x)
            } else {
                return abs(velocity.x) > abs(velocity.y)
            }
        }
        return true
    }
    
    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        // The navigation's swipe back and the chat's scrolling wait for the wheel.
        return gestureRecognizer === self.panRecognizer && !(otherGestureRecognizer.view?.isDescendant(of: self) ?? false)
    }
    
    @objc private func panGesture(_ recognizer: UIPanGestureRecognizer) {
        switch recognizer.state {
        case .began:
            self.isDragging = true
            self.settleAnimator?.invalidate()
            self.settleAnimator = nil
            self.stopNudge()
            self.setHighlighted(false)
            self.dragFrom = self.position
            self.lastTick = max(0, min(self.items.count - 1, Int(self.position.rounded())))
            self.hapticFeedback.prepareTap()
            if let item = self.item(at: self.lastTick) {
                self.scrubbed?(item, 0)
            }
        case .changed:
            let translation = recognizer.translation(in: self)
            let along = self.isVertical ? translation.y : translation.x
            self.position = self.rubberBand(self.dragFrom - along / ChatTopicsRollWheelView.cell)
            self.checkTick()
            self.updateDrum()
        case .ended, .cancelled, .failed:
            if !self.isDragging {
                return
            }
            self.isDragging = false
            let velocityPoint = recognizer.velocity(in: self)
            let velocity = recognizer.state == .ended ? (self.isVertical ? velocityPoint.y : velocityPoint.x) : 0.0
            // A flick carries the wheel on by up to two more topics.
            let last = self.items.count - 1
            let from = self.index(of: self.currentId)
            let carry = max(-2.0, min(2.0, -velocity / ChatTopicsRollWheelView.cell * 0.08))
            var target = max(0, min(last, Int((self.position + carry).rounded())))
            let moved = self.position - self.dragFrom
            if target == from && abs(moved) > 0.28 {
                target = max(0, min(last, from + (moved > 0.0 ? 1 : -1)))
            }
            self.finishTurn(from: from, target: target)
        default:
            break
        }
    }
    
    @objc private func tapGesture(_ recognizer: UITapGestureRecognizer) {
        if case .ended = recognizer.state {
            self.tapped?()
        }
    }
    
    @objc private func longPressGesture(_ recognizer: UILongPressGestureRecognizer) {
        if case .began = recognizer.state, !self.isDragging {
            self.setHighlighted(false)
            self.hapticFeedback.impact(.medium)
            self.longPressed?()
        }
    }
    
    // A press pushes the wheel into its well.
    private func setHighlighted(_ highlighted: Bool) {
        let transition: ComponentTransition = .easeInOut(duration: highlighted ? 0.12 : 0.2)
        transition.setScale(view: self.drumView, scale: highlighted ? 0.96 : 1.0)
    }
    
    override public func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        self.setHighlighted(true)
    }
    
    override public func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        self.setHighlighted(false)
    }
    
    override public func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesCancelled(touches, with: event)
        self.setHighlighted(false)
    }
    
    // MARK: Nudge
    
    private func startNudge(direction: CGFloat) {
        self.nudgeDirection = direction
        self.nudgeStart = CACurrentMediaTime()
        if self.nudgeAnimator == nil {
            self.nudgeAnimator = ConstantDisplayLinkAnimator(update: { [weak self] in
                self?.updateDrum()
            })
        }
        self.nudgeAnimator?.isPaused = false
    }
    
    private func stopNudge() {
        self.nudgeStart = nil
        self.nudgeAnimator?.isPaused = true
    }
    
    // MARK: Typing
    
    // While someone is typing in another topic, the dots of its light keep moving.
    private func updateTypingAnimation() {
        let isTyping = self.window != nil && self.items.contains(where: { self.isTyping($0) })
        if isTyping && self.typingAnimator == nil {
            self.typingAnimator = ConstantDisplayLinkAnimator(update: { [weak self] in
                guard let self else {
                    return
                }
                // Only dots the drum shows need drawing.
                let belt = self.belt
                let first = max(0, Int(floor(belt - 2.2)))
                let last = min(self.items.count - 1, Int(ceil(belt + 2.2)))
                if first <= last && self.items[first ... last].contains(where: { self.isTyping($0) }) {
                    self.overlayView.setNeedsDisplay()
                }
            })
        }
        self.typingAnimator?.isPaused = !isTyping
    }
    
    private func nudgeOffset() -> CGFloat {
        guard let nudgeStart = self.nudgeStart else {
            return 0.0
        }
        let t = CGFloat(CACurrentMediaTime() - nudgeStart)
        if t > 0.9 {
            self.stopNudge()
            return 0.0
        }
        return self.nudgeDirection * 0.32 * sin(t * .pi * 2.0 * 1.9) * exp(-t * 5.2)
    }
    
    // MARK: Layout and drawing
    
    private var belt: CGFloat {
        return (self.beltOverride ?? self.position) + self.nudgeOffset()
    }
    
    override public func layoutSubviews() {
        super.layoutSubviews()
        
        let bounds = self.bounds
        self.drumView.bounds = CGRect(origin: CGPoint(), size: bounds.size)
        self.drumView.center = CGPoint(x: bounds.midX, y: bounds.midY)
        self.drumView.layer.cornerRadius = min(bounds.width, bounds.height) * 0.5
        self.underlayView.frame = CGRect(origin: CGPoint(), size: bounds.size)
        self.iconsContainer.frame = CGRect(origin: CGPoint(), size: bounds.size)
        self.overlayView.frame = CGRect(origin: CGPoint(), size: bounds.size)
        self.layoutBadge()
        self.updateDrum()
    }
    
    private func updateDrum() {
        self.layoutIcons()
        self.underlayView.setNeedsDisplay()
        self.overlayView.setNeedsDisplay()
    }
    
    private func isTyping(_ item: ChatTopicsRollItem) -> Bool {
        return !item.isAll && item.id != self.currentId && item.typingText != nil
    }
    
    // A light on the scale: news, or someone typing there.
    private func isLit(_ item: ChatTopicsRollItem) -> Bool {
        return !item.isAll && item.id != self.currentId && (item.unreadCount > 0 || item.hasMention || item.typingText != nil)
    }
    
    private func drawUnderlay(context: CGContext, rect: CGRect) {
        guard let theme = self.theme, !self.items.isEmpty else {
            return
        }
        let isDark = theme.overallDarkAppearance
        let cornerRadius = min(rect.width, rect.height) * 0.5
        let belt = self.belt
        
        if self.drawsWell {
            ChatTopicsRollDrawing.drawWell(context: context, rect: rect, cornerRadius: cornerRadius, amount: 1.0, isDark: isDark)
        }
        
        // News further along than the wheel shows makes that end of the well glow.
        var loudStart: CGFloat = 0.0, loudEnd: CGFloat = 0.0, quietStart: CGFloat = 0.0, quietEnd: CGFloat = 0.0
        for i in 0 ..< self.items.count {
            let item = self.items[i]
            if !self.isLit(item) {
                continue
            }
            let away = smoothstep(1.3, 2.3, abs(CGFloat(i) - belt))
            let isLoud = !item.isMuted || item.hasMention || item.typingText != nil
            if CGFloat(i) < belt {
                if isLoud { loudStart += away } else { quietStart += away }
            } else {
                if isLoud { loudEnd += away } else { quietEnd += away }
            }
        }
        self.drawEndGlow(context: context, rect: rect, loud: loudStart, quiet: quietStart, atEnd: false, theme: theme)
        self.drawEndGlow(context: context, rect: rect, loud: loudEnd, quiet: quietEnd, atEnd: true, theme: theme)
        
        // Knurled ticks along the lower edge (the right edge, standing up), turning with the drum; a lit topic
        // takes its tick's place.
        let radius = ChatTopicsRollWheelView.radius
        let knurlColor = ChatTopicsRollDrawing.knurlColor(theme: theme)
        context.setLineWidth(1.25)
        context.setLineCap(.round)
        let baseKnurl = Int(floor(belt * 2.0))
        for j in -8 ... 8 {
            let tick = baseKnurl + j
            if tick % 2 == 0 && tick >= 0 && tick / 2 < self.items.count && self.isLit(self.items[tick / 2]) {
                continue
            }
            let angle = (CGFloat(tick) / 2.0 - belt) * ChatTopicsRollWheelView.step
            if abs(angle) > 1.45 {
                continue
            }
            let along = radius * sin(angle)
            context.setStrokeColor(knurlColor.withMultipliedAlpha(cos(angle)).cgColor)
            if self.isVertical {
                context.move(to: CGPoint(x: rect.maxX - ChatTopicsRollWheelView.knurlFromEdge - ChatTopicsRollWheelView.knurl, y: rect.midY + along))
                context.addLine(to: CGPoint(x: rect.maxX - ChatTopicsRollWheelView.knurlFromEdge, y: rect.midY + along))
            } else {
                context.move(to: CGPoint(x: rect.midX + along, y: rect.maxY - ChatTopicsRollWheelView.knurlFromEdge - ChatTopicsRollWheelView.knurl))
                context.addLine(to: CGPoint(x: rect.midX + along, y: rect.maxY - ChatTopicsRollWheelView.knurlFromEdge))
            }
            context.strokePath()
        }
        
        // The bead over the topic you are in: a drop of glass the drum turns under.
        if self.lensAlpha > 0.0 {
            let lensRect = CGRect(x: rect.midX - 16.0, y: rect.midY - 16.0, width: 32.0, height: 32.0)
            ChatTopicsRollDrawing.drawPuck(context: context, rect: lensRect, cornerRadius: 16.0, alpha: self.lensAlpha, isDark: isDark)
        }
    }
    
    private func drawOverlay(context: CGContext, rect: CGRect) {
        guard let theme = self.theme, !self.items.isEmpty else {
            return
        }
        let belt = self.belt
        let radius = ChatTopicsRollWheelView.radius
        
        // Topics with news light up on the scale, turning with the drum like stations on a radio dial; where
        // someone is typing, the light is three dots.
        let first = max(0, Int(floor(belt - 2.2)))
        let last = min(self.items.count - 1, Int(ceil(belt + 2.2)))
        if first <= last {
            for i in first ... last {
                let item = self.items[i]
                if !self.isLit(item) {
                    continue
                }
                let angle = (CGFloat(i) - belt) * ChatTopicsRollWheelView.step
                if abs(angle) > 1.45 {
                    continue
                }
                let c = cos(angle)
                let along = radius * sin(angle)
                let center: CGPoint
                if self.isVertical {
                    center = CGPoint(x: rect.maxX - ChatTopicsRollWheelView.lightFromEdge, y: rect.midY + along)
                } else {
                    center = CGPoint(x: rect.midX + along, y: rect.maxY - ChatTopicsRollWheelView.lightFromEdge)
                }
                ChatTopicsRollDrawing.drawLight(context: context, center: center, squeeze: max(0.35, c), alpha: pow(c, 1.2), isTyping: self.isTyping(item), isMention: item.hasMention, isMuted: item.isMuted && !item.hasMention, isVertical: self.isVertical, theme: theme)
            }
        }
        
        // The drum's ends, shaded where it curves away.
        ChatTopicsRollDrawing.drawEnds(context: context, rect: rect, cornerRadius: min(rect.width, rect.height) * 0.5, length: 20.0, start: 1.0, end: 1.0, isVertical: self.isVertical, isDark: theme.overallDarkAppearance)
    }
    
    // A soft glow inside one end of the well, in the counter's colour (grey when the news there is muted).
    private func drawEndGlow(context: CGContext, rect: CGRect, loud: CGFloat, quiet: CGFloat, atEnd: Bool, theme: PresentationTheme) {
        let amount = min(1.0, loud + 0.6 * quiet)
        if amount <= 0.01 {
            return
        }
        let color = (loud > 0.05 || quiet <= 0.0) ? theme.chatList.unreadBadgeActiveBackgroundColor : theme.chatList.unreadBadgeInactiveBackgroundColor
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [color.withAlphaComponent(0.42 * amount).cgColor, color.withAlphaComponent(0.0).cgColor] as CFArray, locations: [0.0, 1.0]) else {
            return
        }
        let reach: CGFloat = 34.0
        context.saveGState()
        context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: min(rect.width, rect.height) * 0.5).cgPath)
        context.clip()
        if self.isVertical {
            let edge = atEnd ? rect.maxY : rect.minY
            context.clip(to: CGRect(x: rect.minX, y: atEnd ? edge - reach : edge, width: rect.width, height: reach))
            context.translateBy(x: rect.midX, y: edge)
            context.scaleBy(x: rect.width * 0.72, y: reach)
        } else {
            let edge = atEnd ? rect.maxX : rect.minX
            context.clip(to: CGRect(x: atEnd ? edge - reach : edge, y: rect.minY, width: reach, height: rect.height))
            context.translateBy(x: edge, y: rect.midY)
            context.scaleBy(x: reach, y: rect.height * 0.72)
        }
        context.drawRadialGradient(gradient, startCenter: CGPoint(), startRadius: 0.0, endCenter: CGPoint(), endRadius: 1.0, options: [])
        context.restoreGState()
    }
    
    // Topic icons on the drum: full size at the bead, smaller and fainter as they curve away.
    private func layoutIcons() {
        guard let context = self.context, let theme = self.theme, !self.items.isEmpty else {
            return
        }
        let bounds = self.bounds
        let belt = self.belt
        let radius = ChatTopicsRollWheelView.radius
        let first = max(0, Int(floor(belt - 2.2)))
        let last = min(self.items.count - 1, Int(ceil(belt + 2.2)))
        
        var visibleKeys = Set<String>()
        if first <= last {
            for i in first ... last {
                let angle = (CGFloat(i) - belt) * ChatTopicsRollWheelView.step
                if abs(angle) > 1.52 {
                    continue
                }
                let item = self.items[i]
                let c = cos(angle)
                let face = max(0.0, min(1.0, 1.0 - abs(CGFloat(i) - belt)))
                let scale = (0.46 + 0.54 * c) * (1.0 + 0.08 * face)
                let alpha = pow(c, 1.4) * (0.62 + 0.38 * face)
                let along = radius * sin(angle)
                let center = self.isVertical ? CGPoint(x: bounds.midX, y: bounds.midY + along) : CGPoint(x: bounds.midX + along, y: bounds.midY)
                
                let slot = self.slot(for: item, context: context, theme: theme, force: false)
                visibleKeys.insert(slot.key)
                slot.containerView.isHidden = false
                slot.containerView.bounds = CGRect(origin: CGPoint(), size: CGSize(width: ChatTopicsRollWheelView.iconSize, height: ChatTopicsRollWheelView.iconSize))
                slot.containerView.center = center
                slot.containerView.transform = CGAffineTransform(scaleX: scale, y: scale)
                slot.containerView.alpha = alpha
            }
        }
        for (key, slot) in self.slots {
            if !visibleKeys.contains(key) {
                slot.containerView.isHidden = true
            }
        }
    }
    
    private func slotKey(for item: ChatTopicsRollItem) -> String {
        if let id = item.id {
            return "\(id)"
        } else {
            return "all"
        }
    }
    
    private func slot(for item: ChatTopicsRollItem, context: AccountContext, theme: PresentationTheme, force: Bool) -> IconSlot {
        let key = self.slotKey(for: item)
        let slot: IconSlot
        var isNew = false
        if let current = self.slots[key] {
            slot = current
        } else {
            slot = IconSlot(key: key)
            self.slots[key] = slot
            self.iconsContainer.addSubview(slot.containerView)
            isNew = true
        }
        if isNew || force {
            self.updateIcon(slot: slot, item: item, context: context, theme: theme)
        }
        return slot
    }
    
    private func updateIconContents(force: Bool) {
        guard let context = self.context, let theme = self.theme else {
            return
        }
        for item in self.items {
            if let slot = self.slots[self.slotKey(for: item)] {
                self.updateIcon(slot: slot, item: item, context: context, theme: theme)
            }
        }
        self.evictSlots()
    }
    
    private func updateIcon(slot: IconSlot, item: ChatTopicsRollItem, context: AccountContext, theme: PresentationTheme) {
        let size = CGSize(width: ChatTopicsRollWheelView.iconSize, height: ChatTopicsRollWheelView.iconSize)
        let accent = theme.rootController.navigationBar.accentTextColor
        
        var content: EmojiStatusComponent.Content?
        switch item.icon {
        case .all:
            content = .image(image: UIImage(bundleImageName: "Chat List/Tabs/IconChats"), tintColor: accent)
        case .general:
            content = .image(image: PresentationResourcesChatList.generalTopicTemplateIcon(theme), tintColor: accent)
        case let .emoji(fileId):
            content = .animation(content: .customEmoji(fileId: fileId), size: size, placeholderColor: theme.list.mediaPlaceholderColor, themeColor: accent, loopMode: .count(0))
        case let .topic(title, color):
            content = .topic(title: title, color: color, size: size)
        case .peer:
            content = nil
        }
        
        if let content {
            if let avatarNode = slot.avatarNode {
                slot.avatarNode = nil
                avatarNode.view.removeFromSuperview()
            }
            let icon: ComponentView<Empty>
            if let current = slot.icon {
                icon = current
            } else {
                icon = ComponentView()
                slot.icon = icon
            }
            let _ = icon.update(
                transition: .immediate,
                component: AnyComponent(EmojiStatusComponent(
                    context: context,
                    animationCache: context.animationCache,
                    animationRenderer: context.animationRenderer,
                    content: content,
                    isVisibleForAnimations: false,
                    action: nil
                )),
                environment: {},
                containerSize: size
            )
            if let iconView = icon.view {
                if iconView.superview == nil {
                    iconView.isUserInteractionEnabled = false
                    slot.containerView.addSubview(iconView)
                }
                iconView.frame = CGRect(origin: CGPoint(), size: size)
            }
        } else if case let .peer(peer) = item.icon {
            if let icon = slot.icon {
                slot.icon = nil
                icon.view?.removeFromSuperview()
            }
            let avatarNode: AvatarNode
            if let current = slot.avatarNode {
                avatarNode = current
            } else {
                avatarNode = AvatarNode(font: avatarPlaceholderFont(size: 9.0))
                avatarNode.isUserInteractionEnabled = false
                slot.avatarNode = avatarNode
                slot.containerView.addSubview(avatarNode.view)
            }
            avatarNode.frame = CGRect(origin: CGPoint(), size: size)
            avatarNode.updateSize(size: size)
            avatarNode.setPeer(context: context, theme: theme, peer: peer, overrideImage: nil, emptyColor: .gray, clipStyle: .round, synchronousLoad: false, displayDimensions: size)
        }
    }
    
    private func evictSlots() {
        var removeKeys: [String] = []
        for key in self.slots.keys {
            let index: Int?
            if key == "all" {
                index = self.items.isEmpty ? nil : 0
            } else if let id = Int64(key) {
                index = self.indexById[id]
            } else {
                index = nil
            }
            // Topics that left the list, and icons far from the lens, let their images go.
            if let index {
                if self.slots.count > 16 && abs(CGFloat(index) - self.position) > 5.0 {
                    removeKeys.append(key)
                }
            } else {
                removeKeys.append(key)
            }
        }
        for key in removeKeys {
            if let slot = self.slots.removeValue(forKey: key) {
                slot.containerView.removeFromSuperview()
            }
        }
    }
    
    // MARK: Badge
    
    // Unread messages in the other topics, on the rim of the well at its far end, clear of the icons.
    private func updateBadge(animated: Bool) {
        guard let theme = self.theme else {
            return
        }
        var unread = 0
        var mutedUnread = 0
        var mention = false
        for item in self.items {
            if item.isAll || item.id == self.currentId {
                continue
            }
            if item.isMuted && !item.hasMention {
                mutedUnread += item.unreadCount
            } else {
                unread += item.unreadCount
            }
            mention = mention || item.hasMention
        }
        let isMuted = unread <= 0 && !mention
        let count = isMuted ? mutedUnread : unread
        let show = count > 0 || mention
        let pop = animated && show && (count > self.badgeCount || (mention && !self.badgeHasMention))
        
        let wasShown = self.hasBadge
        self.hasBadge = show
        self.badgeIsMuted = isMuted
        self.badgeCount = count
        self.badgeHasMention = mention
        
        if show {
            var text = mention ? "@" : ""
            if count > 0 {
                text += count > 999 ? "999+" : countString(Int64(count))
            }
            self.badgeLabel.text = text
            self.badgeLabel.textColor = isMuted ? theme.chatList.unreadBadgeInactiveTextColor : theme.chatList.unreadBadgeActiveTextColor
            // The pill with a ring of the background's colour around it, so it reads over the glass.
            self.badgeBackgroundView.image = generateStretchableFilledCircleImage(diameter: 20.0, color: isMuted ? theme.chatList.unreadBadgeInactiveBackgroundColor : theme.chatList.unreadBadgeActiveBackgroundColor, strokeColor: theme.list.plainBackgroundColor, strokeWidth: 1.5)
            self.layoutBadge()
        }
        
        let transition: ComponentTransition = animated ? .easeInOut(duration: 0.32) : .immediate
        if show != wasShown {
            transition.setAlpha(view: self.badgeView, alpha: show ? 1.0 : 0.0)
            transition.setScale(view: self.badgeView, scale: show ? 1.0 : 0.6)
        }
        if pop {
            self.badgeView.layer.removeAnimation(forKey: "pop")
            let animation = CAKeyframeAnimation(keyPath: "transform.scale")
            animation.values = [0.7 as NSNumber, 1.22 as NSNumber, 1.0 as NSNumber]
            animation.keyTimes = [0.0 as NSNumber, 0.6 as NSNumber, 1.0 as NSNumber]
            animation.duration = 0.42
            animation.timingFunctions = [CAMediaTimingFunction(name: .easeOut), CAMediaTimingFunction(name: .easeOut)]
            self.badgeView.layer.add(animation, forKey: "pop")
        }
    }
    
    private func layoutBadge() {
        let textSize = self.badgeLabel.sizeThatFits(CGSize(width: 100.0, height: 100.0))
        let height: CGFloat = 17.0
        let width = max(height, ceil(textSize.width) + 9.0)
        // On the well's rim at its far end, low enough to stay on the island's glass.
        let bounds = self.bounds
        let badgeFrame = CGRect(x: bounds.maxX + 3.0 - width, y: bounds.minY - height * 0.5 + 3.0, width: width, height: height)
        self.badgeView.bounds = CGRect(origin: CGPoint(), size: badgeFrame.size)
        self.badgeView.center = CGPoint(x: badgeFrame.midX, y: badgeFrame.midY)
        self.badgeBackgroundView.frame = CGRect(origin: CGPoint(), size: badgeFrame.size).insetBy(dx: -1.5, dy: -1.5)
        self.badgeLabel.frame = CGRect(origin: CGPoint(), size: badgeFrame.size)
    }
    
    /// What the badge says (unread messages in the other topics), or nil when it is hidden.
    public var badgeText: String? {
        return self.hasBadge ? self.badgeLabel.text : nil
    }
    
    /// The names of the other topics with news, as many as `max`, or nil when there is none.
    public func newsTitles(max: Int) -> String? {
        let onlyMuted = self.badgeIsMuted
        var titles: [String] = []
        for item in self.items {
            if titles.count >= max {
                break
            }
            if item.isAll || item.id == self.currentId || (item.unreadCount <= 0 && !item.hasMention) {
                continue
            }
            if item.isMuted && !item.hasMention && !onlyMuted {
                continue
            }
            titles.append(item.title)
        }
        return titles.isEmpty ? nil : titles.joined(separator: ", ")
    }
    
    // MARK: Accessibility: the wheel is a button that also scrolls through the topics.
    
    private func updateAccessibility() {
        let item = self.item(at: self.index(of: self.currentId))
        self.accessibilityLabel = item?.title
        self.accessibilityValue = self.badgeText
        self.accessibilityHint = self.strings?.Chat_TopicsRoll_WheelHint
    }
    
    override public func accessibilityIncrement() {
        self.turn(by: 1)
    }
    
    override public func accessibilityDecrement() {
        self.turn(by: -1)
    }
    
    override public func accessibilityActivate() -> Bool {
        self.tapped?()
        return true
    }
}
