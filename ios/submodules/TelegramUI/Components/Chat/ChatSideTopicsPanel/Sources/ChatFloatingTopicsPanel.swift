import Foundation
import UIKit
import Display
import ComponentFlow
import AccountContext
import TelegramPresentationData
import TelegramCore
import GlassBackgroundComponent
import HeaderPanelContainerComponent

/// How a topic bar that rolls up into a wheel is shown.
public struct ChatTopicsRollState: Equatable {
    /// The group's remembered choice: the bar is rolled up into the wheel.
    public var isRolledUp: Bool
    /// The bar is out for a moment, opened from the wheel: picking a topic winds it back up.
    public var isTemporarilyOut: Bool
    
    public init(isRolledUp: Bool, isTemporarilyOut: Bool) {
        self.isRolledUp = isRolledUp
        self.isTemporarilyOut = isTemporarilyOut
    }
    
    /// The wheel shows, rather than the bar.
    public var showsWheel: Bool {
        return self.isRolledUp && !self.isTemporarilyOut
    }
}

/// What a rolling bar asks of the chat.
public final class ChatTopicsRollActions {
    /// The wheel was tapped: the bar rolls out for a moment.
    public let rollOut: () -> Void
    /// Wind the bar up into the wheel (`«`, or a topic was picked from a bar that was out for a moment).
    public let windUp: () -> Void
    /// Keep the bar out, the classic way.
    public let keepOpen: () -> Void
    
    public init(rollOut: @escaping () -> Void, windUp: @escaping () -> Void, keepOpen: @escaping () -> Void) {
        self.rollOut = rollOut
        self.windUp = windUp
        self.keepOpen = keepOpen
    }
}

/// The glass island the wheel turns in: the wheel sits `inset` from its edges, so their rounded ends are
/// concentric.
public enum ChatTopicsRollIsland {
    public static func inset(rowHeight: CGFloat) -> CGFloat {
        return floor((rowHeight - ChatTopicsRollWheelView.thickness) * 0.5)
    }
    
    public static func width(rowHeight: CGFloat) -> CGFloat {
        return ChatTopicsRollWheelView.length + inset(rowHeight: rowHeight) * 2.0
    }
    
    /// The left inset of the panel that shares the island's row, so its line sits 10 points after the well (the
    /// pinned message draws its line 18 points into its left inset).
    public static func sharedPanelLeftInset(rowHeight: CGFloat) -> CGFloat {
        return width(rowHeight: rowHeight) - inset(rowHeight: rowHeight) + 10.0 - 18.0
    }
}

/// What the display shows: the topic under the lens while the wheel turns. It lets go 0.5 s after the wheel
/// settles, and the topic's own pinned message comes back.
private final class ChatTopicsRollDisplayState {
    private(set) var item: ChatTopicsRollItem?
    private(set) var direction: Int = 0
    private var letGoWorkItem: DispatchWorkItem?
    
    /// The display started or stopped showing.
    var shownUpdated: (() -> Void)?
    /// The display tuned from one topic to another.
    var contentUpdated: (() -> Void)?
    
    deinit {
        self.letGoWorkItem?.cancel()
    }
    
    func scrubbed(item: ChatTopicsRollItem?, direction: Int) {
        if let item {
            self.letGoWorkItem?.cancel()
            self.letGoWorkItem = nil
            let wasShown = self.item != nil
            self.item = item
            if direction != 0 {
                self.direction = direction
            }
            if wasShown {
                self.contentUpdated?()
            } else {
                self.shownUpdated?()
            }
        } else if self.item != nil {
            self.letGoWorkItem?.cancel()
            let workItem = DispatchWorkItem { [weak self] in
                guard let self else {
                    return
                }
                self.letGoWorkItem = nil
                if self.item != nil {
                    self.item = nil
                    self.shownUpdated?()
                }
            }
            self.letGoWorkItem = workItem
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: workItem)
        }
    }
    
    /// The topics changed (news, someone typing): the topic on the display follows, in place.
    func itemsUpdated(_ items: [ChatTopicsRollItem]) {
        guard let current = self.item, let updated = items.first(where: { $0.id == current.id }), updated != current else {
            return
        }
        self.item = updated
        self.contentUpdated?()
    }
    
    func reset() {
        self.letGoWorkItem?.cancel()
        self.letGoWorkItem = nil
        self.item = nil
    }
}

private func easeInOutCubic(_ t: CGFloat) -> CGFloat {
    return t < 0.5 ? 4.0 * t * t * t : 1.0 - pow(-2.0 * t + 2.0, 3.0) * 0.5
}

/// While the bar rolls in or out, the wheel turns to show the tab it is passing: from the bar's far end to your
/// topic as it winds up, the other way as it rolls out.
private func animateRollBelt(wheelView: ChatTopicsRollWheelView, itemCount: Int, windingUp: Bool, duration: Double) -> DisplayLinkAnimator? {
    if itemCount < 2 {
        return nil
    }
    let current = CGFloat(wheelView.currentIndex)
    let far = min(CGFloat(itemCount - 1), current + 3.0)
    if far == current {
        return nil
    }
    let from = windingUp ? far : current
    let to = windingUp ? current : far
    return DisplayLinkAnimator(duration: duration, from: 0.0, to: 1.0, update: { [weak wheelView] t in
        wheelView?.setBeltOverride(from + (to - from) * easeInOutCubic(t))
    }, completion: { [weak wheelView] in
        wheelView?.setBeltOverride(nil)
    })
}

/// The gear line, the wheel's knurled ticks, shows only while the bar winds up or rolls out: winding up, subtly
/// at first until it is the wheel's own pale ticks; rolling out runs in reverse. None while it is out or kept open.
private func animateGearLine(view: UIView, windingUp: Bool, duration: Double) {
    let layer = view.layer
    layer.removeAnimation(forKey: "gear")
    view.alpha = 0.0
    let animation = CABasicAnimation(keyPath: "opacity")
    animation.fromValue = (windingUp ? 0.0 : 1.0) as NSNumber
    animation.toValue = (windingUp ? 1.0 : 0.0) as NSNumber
    animation.duration = duration
    animation.timingFunction = CAMediaTimingFunction(name: windingUp ? .easeIn : .easeOut)
    layer.add(animation, forKey: "gear")
}

private func transitionDuration(_ transition: ComponentTransition) -> Double? {
    if case let .curve(duration, _) = transition.animation {
        return duration
    }
    return nil
}

public final class ChatFloatingTopicsPanel: Component {
    public typealias EnvironmentType = ChatSidePanelEnvironment
    
    public let context: AccountContext
    public let theme: PresentationTheme
    public let preferClearGlass: Bool
    public let strings: PresentationStrings
    public let location: ChatSideTopicsPanel.Location
    public let peerId: EnginePeer.Id
    public let kind: ChatSideTopicsPanel.Kind
    public let topicId: Int64?
    public let controller: () -> ViewController?
    public let togglePanel: () -> Void
    public let updateTopicId: (Int64?, ChatControllerAnimateInnerChatSwitchDirection) -> Void
    public let openDeletePeer: (Int64) -> Void
    public let roll: ChatTopicsRollState?
    public let rollActions: ChatTopicsRollActions?
    
    public init(
        context: AccountContext,
        theme: PresentationTheme,
        preferClearGlass: Bool,
        strings: PresentationStrings,
        location: ChatSideTopicsPanel.Location,
        peerId: EnginePeer.Id,
        kind: ChatSideTopicsPanel.Kind,
        topicId: Int64?,
        controller: @escaping () -> ViewController?,
        togglePanel: @escaping () -> Void,
        updateTopicId: @escaping (Int64?, ChatControllerAnimateInnerChatSwitchDirection) -> Void,
        openDeletePeer: @escaping (Int64) -> Void,
        roll: ChatTopicsRollState? = nil,
        rollActions: ChatTopicsRollActions? = nil
    ) {
        self.context = context
        self.theme = theme
        self.preferClearGlass = preferClearGlass
        self.strings = strings
        self.location = location
        self.peerId = peerId
        self.kind = kind
        self.topicId = topicId
        self.controller = controller
        self.togglePanel = togglePanel
        self.updateTopicId = updateTopicId
        self.openDeletePeer = openDeletePeer
        self.roll = roll
        self.rollActions = rollActions
    }
    
    public static func ==(lhs: ChatFloatingTopicsPanel, rhs: ChatFloatingTopicsPanel) -> Bool {
        if lhs.context !== rhs.context {
            return false
        }
        if lhs.theme !== rhs.theme {
            return false
        }
        if lhs.preferClearGlass != rhs.preferClearGlass {
            return false
        }
        if lhs.strings !== rhs.strings {
            return false
        }
        if lhs.location != rhs.location {
            return false
        }
        if lhs.peerId != rhs.peerId {
            return false
        }
        if lhs.kind != rhs.kind {
            return false
        }
        if lhs.topicId != rhs.topicId {
            return false
        }
        if lhs.roll != rhs.roll {
            return false
        }
        if lhs.rollActions !== rhs.rollActions {
            return false
        }
        return true
    }
    
    public final class View: UIView {
        private let containerView: GlassBackgroundContainerView
        
        private var sharedPanelBackgroundView: GlassBackgroundView?
        private var sidePanel: ComponentView<ChatSidePanelEnvironment>?
        private var topPanel: ComponentView<ChatSidePanelEnvironment>?
        
        // Side tabs roll up into the wheel standing up, in an island at the top of the rail.
        private var wheelView: ChatTopicsRollWheelView?
        private var sideTrackView: UIImageView?
        private var sideGearLineView: UIImageView?
        private var displayBackgroundView: GlassBackgroundView?
        private var displayView: ChatTopicsRollDisplayView?
        private let display = ChatTopicsRollDisplayState()
        private var rollItems: [ChatTopicsRollItem] = []
        private var beltAnimator: DisplayLinkAnimator?
        private var gearTheme: PresentationTheme?
        private var trackTheme: PresentationTheme?
        
        private var component: ChatFloatingTopicsPanel?
        private weak var state: EmptyComponentState?
        private var isUpdating = false
        
        override public init(frame: CGRect) {
            self.containerView = GlassBackgroundContainerView()
            
            super.init(frame: frame)
            
            self.addSubview(self.containerView)
            
            self.display.shownUpdated = { [weak self] in
                guard let self, !self.isUpdating else {
                    return
                }
                self.state?.updated(transition: .spring(duration: 0.4))
            }
            self.display.contentUpdated = { [weak self] in
                self?.updateDisplayContent(animated: true)
            }
        }
        
        required public init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }
        
        override public func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
            guard let result = super.hitTest(point, with: event) else {
                return nil
            }
            if result === self || result === self.containerView.contentView {
                return nil
            }
            return result
        }
        
        func update(component: ChatFloatingTopicsPanel, availableSize: CGSize, state: EmptyComponentState, environment: Environment<ChatSidePanelEnvironment>, transition: ComponentTransition) -> CGSize {
            self.isUpdating = true
            defer {
                self.isUpdating = false
            }
            
            let previousComponent = self.component
            self.component = component
            self.state = state
            
            let environment = environment[ChatSidePanelEnvironment.self].value
            
            var currentPanelBackgroundFrame: CGRect?
            var currentPanelCornerRadius: CGFloat = 20.0
            
            if case .side = component.location {
                let roll = component.roll
                let showsWheel = roll?.showsWheel ?? false
                let wasShowingWheel = previousComponent?.roll?.showsWheel ?? showsWheel
                let isRollTransition = previousComponent != nil && wasShowingWheel != showsWheel && !transition.animation.isImmediate
                
                var rollControl: ChatTopicsRollControlView.Mode?
                if let roll {
                    rollControl = roll.isRolledUp ? .keepOpen : .windUp
                }
                
                let sidePanel: ComponentView<ChatSidePanelEnvironment>
                var sidePanelTransition = transition
                if let current = self.sidePanel {
                    sidePanel = current
                } else {
                    sidePanelTransition = sidePanelTransition.withAnimation(.none)
                    sidePanel = ComponentView()
                    self.sidePanel = sidePanel
                }
                let sidePanelSize = sidePanel.update(
                    transition: sidePanelTransition,
                    component: AnyComponent(ChatSideTopicsPanel(
                        context: component.context,
                        theme: component.theme,
                        strings: component.strings,
                        location: .side,
                        peerId: component.peerId,
                        kind: component.kind,
                        topicId: component.topicId,
                        controller: component.controller,
                        togglePanel: component.togglePanel,
                        updateTopicId: { [weak self] threadId, direction in
                            guard let self, let component = self.component else {
                                return
                            }
                            component.updateTopicId(threadId, direction ? .down : .up)
                            self.windUpAfterPicking()
                        },
                        openDeletePeer: component.openDeletePeer,
                        rollControl: rollControl,
                        rollControlAction: rollControl == nil ? nil : { [weak self] in
                            guard let self, let component = self.component, let roll = component.roll else {
                                return
                            }
                            if roll.isRolledUp {
                                component.rollActions?.keepOpen()
                            } else {
                                component.rollActions?.windUp()
                            }
                        }
                    )),
                    environment: {
                        ChatSidePanelEnvironment(insets: UIEdgeInsets(
                            top: 0.0,
                            left: 0.0,
                            bottom: environment.insets.bottom,
                            right: 0.0
                        ))
                    },
                    containerSize: CGSize(width: 72.0 + 8.0, height: availableSize.height)
                )
                
                // Rolled up, the rail is wound into a 48 × 120 island at its top; tapped, it drops down out of it like
                // a blind, and picking a topic rolls it back up into it.
                let islandFrame = CGRect(origin: CGPoint(x: 16.0, y: 8.0), size: CGSize(width: ChatTopicsRollWheelView.thickness + 12.0, height: ChatTopicsRollWheelView.length + 12.0))
                let railHeight = availableSize.height - 8.0 - environment.insets.bottom
                let sidePanelFrame: CGRect
                let sidePanelBackgroundFrame: CGRect
                if showsWheel {
                    sidePanelFrame = CGRect(origin: CGPoint(x: 8.0, y: 0.0), size: CGSize(width: 16.0 + 80.0, height: islandFrame.maxY))
                    sidePanelBackgroundFrame = islandFrame
                    currentPanelCornerRadius = islandFrame.width * 0.5
                } else {
                    sidePanelFrame = CGRect(origin: CGPoint(x: 8.0, y: 0.0), size: CGSize(width: 16.0 + 80.0, height: railHeight))
                    sidePanelBackgroundFrame = CGRect(origin: CGPoint(x: 16.0, y: 8.0), size: CGSize(width: 80.0, height: availableSize.height - 8.0 - 8.0 - environment.insets.bottom))
                }
                currentPanelBackgroundFrame = sidePanelBackgroundFrame
                if let sidePanelView = sidePanel.view as? ChatSideTopicsPanel.View {
                    if sidePanelView.superview == nil {
                        sidePanelView.layer.cornerRadius = 20.0
                        sidePanelView.clipsToBounds = true
                        self.addSubview(sidePanelView)
                        sidePanelView.itemsUpdated = { [weak self, weak sidePanelView] items in
                            self?.rawItemsUpdated(items, typing: sidePanelView?.typing ?? [:], isVertical: true)
                        }
                        sidePanelView.typingUpdated = { [weak self, weak sidePanelView] typing in
                            guard let sidePanelView else {
                                return
                            }
                            self?.rawItemsUpdated(sidePanelView.items, typing: typing, isVertical: true)
                        }
                        
                        sidePanelView.frame = CGRect(origin: CGPoint(x: 8.0, y: 0.0), size: CGSize(width: sidePanelSize.height, height: 8.0 + 40.0))
                        if showsWheel {
                            sidePanelView.alpha = 0.0
                        }
                    }
                    transition.setFrame(view: sidePanelView, frame: sidePanelFrame)
                    sidePanelView.accessibilityElementsHidden = showsWheel
                    
                    // The rail's tabs lie on the wheel's track standing up, which drops down out of the wheel's well
                    // with the rail and winds back up into it.
                    let trackView: UIImageView
                    if let current = self.sideTrackView {
                        trackView = current
                    } else {
                        trackView = UIImageView()
                        trackView.isUserInteractionEnabled = false
                        trackView.alpha = sidePanelView.alpha
                        self.sideTrackView = trackView
                        self.insertSubview(trackView, belowSubview: sidePanelView)
                    }
                    if self.trackTheme !== component.theme {
                        self.trackTheme = component.theme
                        trackView.image = ChatTopicsRollDrawing.generateTrackImage(theme: component.theme, cornerRadius: 16.0, isVertical: true)
                    }
                    let trackInset: CGFloat = showsWheel ? 6.0 : 4.0
                    let trackFrame = sidePanelBackgroundFrame.insetBy(dx: trackInset, dy: trackInset)
                    if trackView.bounds.isEmpty {
                        trackView.frame = trackFrame
                    } else {
                        transition.setFrame(view: trackView, frame: trackFrame)
                    }
                    
                    for view in [sidePanelView, trackView] as [UIView] {
                        if isRollTransition, let duration = transitionDuration(transition) {
                            if showsWheel {
                                // The tabs go as the blind reaches the island.
                                ComponentTransition.easeInOut(duration: duration * 0.35).setAlpha(view: view, alpha: 0.0, delay: duration * 0.65)
                            } else {
                                ComponentTransition.easeInOut(duration: 0.15).setAlpha(view: view, alpha: 1.0)
                            }
                        } else {
                            view.alpha = showsWheel ? 0.0 : 1.0
                        }
                    }
                    
                    if roll != nil {
                        // The rail's gear line runs down its track's right edge, and stays while its tabs fade.
                        let gearLineView: UIImageView
                        if let current = self.sideGearLineView {
                            gearLineView = current
                        } else {
                            gearLineView = UIImageView()
                            gearLineView.isUserInteractionEnabled = false
                            gearLineView.alpha = 0.0
                            self.sideGearLineView = gearLineView
                            self.addSubview(gearLineView)
                        }
                        if self.gearTheme !== component.theme {
                            self.gearTheme = component.theme
                            gearLineView.image = ChatTopicsRollDrawing.generateGearLineImage(theme: component.theme, isVertical: true)?.resizableImage(withCapInsets: UIEdgeInsets(), resizingMode: .tile)
                        }
                        let gearLineFrame = CGRect(x: trackFrame.maxX - 3.0 - 3.5, y: trackFrame.minY + 16.0, width: 3.5, height: max(0.0, trackFrame.height - 32.0))
                        transition.setFrame(view: gearLineView, frame: gearLineFrame)
                        if isRollTransition, let duration = transitionDuration(transition) {
                            animateGearLine(view: gearLineView, windingUp: showsWheel, duration: duration)
                        }
                    }
                }
                
                if let roll {
                    let _ = roll
                    let wheelView: ChatTopicsRollWheelView
                    if let current = self.wheelView {
                        wheelView = current
                    } else {
                        wheelView = ChatTopicsRollWheelView()
                        self.wheelView = wheelView
                        self.addSubview(wheelView)
                        wheelView.tapped = { [weak self] in
                            self?.component?.rollActions?.rollOut()
                        }
                        wheelView.longPressed = { [weak self] in
                            // The bar's tabs carry each topic's options.
                            self?.component?.rollActions?.rollOut()
                        }
                        wheelView.turned = { [weak self] item, direction in
                            guard let self, let component = self.component else {
                                return
                            }
                            component.updateTopicId(item.id, direction > 0 ? .down : .up)
                        }
                        wheelView.scrubbed = { [weak self] item, direction in
                            self?.display.scrubbed(item: item, direction: direction)
                        }
                        wheelView.alpha = showsWheel ? 1.0 : 0.0
                    }
                    if self.rollItems.isEmpty, let sidePanelView = sidePanel.view as? ChatSideTopicsPanel.View, !sidePanelView.items.isEmpty {
                        self.rollItems = ChatTopicsRollItem.makeItems(context: component.context, kind: component.kind, rawItems: sidePanelView.items, typing: sidePanelView.typing)
                    }
                    wheelView.update(context: component.context, theme: component.theme, strings: component.strings, items: self.rollItems, currentId: component.topicId, isVertical: true, animated: !transition.animation.isImmediate)
                    wheelView.frame = CGRect(origin: CGPoint(x: islandFrame.minX + 6.0, y: islandFrame.minY + 6.0), size: CGSize(width: ChatTopicsRollWheelView.thickness, height: ChatTopicsRollWheelView.length))
                    wheelView.isUserInteractionEnabled = showsWheel
                    wheelView.accessibilityElementsHidden = !showsWheel
                    if isRollTransition, let duration = transitionDuration(transition) {
                        if showsWheel {
                            ComponentTransition.easeInOut(duration: duration * 0.4).setAlpha(view: wheelView, alpha: 1.0, delay: duration * 0.5)
                        } else {
                            ComponentTransition.easeInOut(duration: 0.15).setAlpha(view: wheelView, alpha: 0.0)
                        }
                        self.beltAnimator?.invalidate()
                        self.beltAnimator = animateRollBelt(wheelView: wheelView, itemCount: self.rollItems.count, windingUp: showsWheel, duration: duration)
                    } else {
                        wheelView.alpha = showsWheel ? 1.0 : 0.0
                    }
                    
                    if !showsWheel {
                        self.display.reset()
                    }
                    self.updateSideDisplay(islandFrame: islandFrame, availableSize: availableSize, isShown: showsWheel && self.display.item != nil, transition: transition)
                }
            } else if let sidePanel = self.sidePanel {
                self.sidePanel = nil
                if let sideTrackView = self.sideTrackView {
                    self.sideTrackView = nil
                    transition.setAlpha(view: sideTrackView, alpha: 0.0, completion: { [weak sideTrackView] _ in
                        sideTrackView?.removeFromSuperview()
                    })
                }
                if let sidePanelView = sidePanel.view as? ChatSideTopicsPanel.View {
                    sidePanelView.clipsToBounds = true
                    transition.setFrame(view: sidePanelView, frame: CGRect(origin: CGPoint(x: 8.0, y: 0.0), size: CGSize(width: sidePanelView.bounds.width, height: 8.0 + 40.0)), completion: { [weak sidePanelView] _ in
                        sidePanelView?.removeFromSuperview()
                    })
                }
            }
            
            switch component.location {
            case .top, .bottom:
                let topPanel: ComponentView<ChatSidePanelEnvironment>
                var topPanelTransition = transition
                if let current = self.topPanel {
                    topPanel = current
                } else {
                    topPanelTransition = topPanelTransition.withAnimation(.none)
                    topPanel = ComponentView()
                    self.topPanel = topPanel
                }
                let _ = topPanel.update(
                    transition: topPanelTransition,
                    component: AnyComponent(ChatSideTopicsPanel(
                        context: component.context,
                        theme: component.theme,
                        strings: component.strings,
                        location: component.location == .top ? .top : .bottom,
                        peerId: component.peerId,
                        kind: component.kind,
                        topicId: component.topicId,
                        controller: component.controller,
                        togglePanel: component.togglePanel,
                        updateTopicId: { [weak self] threadId, direction in
                            guard let self, let component = self.component else {
                                return
                            }
                            component.updateTopicId(threadId, direction ? .right : .left)
                        },
                        openDeletePeer: component.openDeletePeer
                    )),
                    environment: {
                        ChatSidePanelEnvironment(insets: UIEdgeInsets(
                            top: 0.0,
                            left: 0.0,
                            bottom: 0.0,
                            right: 0.0
                        ))
                    },
                    containerSize: CGSize(width: availableSize.width - 16.0, height: 8.0 + 40.0)
                )
                let topPanelFrame = CGRect(origin: CGPoint(x: 8.0, y: 0.0), size: CGSize(width: availableSize.width - 16.0, height: 8.0 + 40.0))
                let topPanelBackgroundFrame = CGRect(origin: CGPoint(x: 16.0, y: 8.0), size: CGSize(width: availableSize.width - 16.0 - 16.0, height: 40.0))
                currentPanelBackgroundFrame = topPanelBackgroundFrame
                if let topPanelView = topPanel.view as? ChatSideTopicsPanel.View {
                    if topPanelView.superview == nil {
                        topPanelView.clipsToBounds = true
                        topPanelView.layer.cornerRadius = 20.0
                        self.addSubview(topPanelView)
                        topPanelView.frame = CGRect(origin: CGPoint(x: 8.0, y: 0.0), size: CGSize(width: 80.0 + 16.0, height: topPanelFrame.height))
                    }
                    transition.setFrame(view: topPanelView, frame: topPanelFrame)
                }
            default:
                if let topPanel = self.topPanel {
                    self.topPanel = nil
                    if let topPanelView = topPanel.view as? ChatSideTopicsPanel.View {
                        topPanelView.clipsToBounds = true
                        transition.setFrame(view: topPanelView, frame: CGRect(origin: CGPoint(x: 8.0, y: 0.0), size: CGSize(width: 16.0 + 72.0, height: topPanelView.bounds.height)), completion: { [weak topPanelView] _ in
                            topPanelView?.removeFromSuperview()
                        })
                    }
                }
            }
            
            if let currentPanelBackgroundFrame {
                let sharedPanelBackgroundView: GlassBackgroundView
                if let current = self.sharedPanelBackgroundView {
                    sharedPanelBackgroundView = current
                } else {
                    sharedPanelBackgroundView = GlassBackgroundView()
                    self.sharedPanelBackgroundView = sharedPanelBackgroundView
                    self.containerView.contentView.insertSubview(sharedPanelBackgroundView, at: 0)
                }
                
                transition.setFrame(view: sharedPanelBackgroundView, frame: currentPanelBackgroundFrame)
                
                let defaultGlassTintColor: GlassBackgroundView.TintColor
                if component.preferClearGlass {
                    defaultGlassTintColor = .init(kind: .clear)
                } else {
                    defaultGlassTintColor = .init(kind: .panel)
                }
                
                sharedPanelBackgroundView.update(size: currentPanelBackgroundFrame.size, cornerRadius: currentPanelCornerRadius, isDark: component.theme.overallDarkAppearance, tintColor: defaultGlassTintColor, transition: transition)
            }
            
            transition.setFrame(view: self.containerView, frame: CGRect(origin: CGPoint(), size: availableSize))
            self.containerView.update(size: availableSize, isDark: component.theme.overallDarkAppearance, transition: transition)
            
            return availableSize
        }
        
        // With side tabs rolled up, the display brings its own glass beside the island.
        private func updateSideDisplay(islandFrame: CGRect, availableSize: CGSize, isShown: Bool, transition: ComponentTransition) {
            guard let component = self.component else {
                return
            }
            let displayFrame = CGRect(x: islandFrame.maxX + 8.0, y: islandFrame.minY + floor((islandFrame.height - 50.0) * 0.5), width: max(0.0, availableSize.width - islandFrame.maxX - 8.0 - 16.0), height: 50.0)
            if !isShown && self.displayBackgroundView == nil {
                return
            }
            
            let displayBackgroundView: GlassBackgroundView
            if let current = self.displayBackgroundView {
                displayBackgroundView = current
            } else {
                displayBackgroundView = GlassBackgroundView()
                displayBackgroundView.isUserInteractionEnabled = false
                self.displayBackgroundView = displayBackgroundView
                self.containerView.contentView.addSubview(displayBackgroundView)
                displayBackgroundView.frame = displayFrame
                displayBackgroundView.alpha = 0.0
            }
            let displayView: ChatTopicsRollDisplayView
            if let current = self.displayView {
                displayView = current
            } else {
                displayView = ChatTopicsRollDisplayView()
                self.displayView = displayView
                displayBackgroundView.contentView.addSubview(displayView)
            }
            
            transition.setFrame(view: displayBackgroundView, frame: displayFrame)
            displayBackgroundView.update(size: displayFrame.size, cornerRadius: displayFrame.height * 0.5, isDark: component.theme.overallDarkAppearance, tintColor: .init(kind: component.preferClearGlass ? .clear : .panel), transition: transition)
            displayView.frame = CGRect(origin: CGPoint(), size: displayFrame.size)
            transition.setAlpha(view: displayBackgroundView, alpha: isShown ? 1.0 : 0.0)
            if isShown {
                transition.setScale(view: displayBackgroundView, scale: 1.0)
            } else {
                transition.setScale(view: displayBackgroundView, scale: 0.92)
            }
            self.updateDisplayContent(animated: !transition.animation.isImmediate)
        }
        
        private func updateDisplayContent(animated: Bool) {
            guard let component = self.component, let displayView = self.displayView else {
                return
            }
            displayView.update(item: self.display.item, newsTitles: self.wheelView?.newsTitles(max: 3), direction: self.display.direction, theme: component.theme, strings: component.strings, style: .row, size: displayView.bounds.size, animated: animated)
        }
        
        private func rawItemsUpdated(_ items: [EngineChatList.Item], typing: [Int64: String], isVertical: Bool) {
            guard let component = self.component, let wheelView = self.wheelView else {
                return
            }
            self.rollItems = ChatTopicsRollItem.makeItems(context: component.context, kind: component.kind, rawItems: items, typing: typing)
            wheelView.update(context: component.context, theme: component.theme, strings: component.strings, items: self.rollItems, currentId: component.topicId, isVertical: isVertical, animated: true)
            self.display.itemsUpdated(self.rollItems)
        }
        
        // Picking a topic from a bar that is out for a moment winds it back up 0.34 s later.
        private func windUpAfterPicking() {
            guard let component = self.component, let roll = component.roll, roll.isTemporarilyOut else {
                return
            }
            let rollActions = component.rollActions
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.34, execute: {
                rollActions?.windUp()
            })
        }
        
        public func topicIndex(threadId: Int64?) -> Int? {
            if let sidePanelView = self.sidePanel?.view as? ChatSideTopicsPanel.View {
                return sidePanelView.topicIndex(threadId: threadId)
            } else if let topPanelView = self.topPanel?.view as? ChatSideTopicsPanel.View {
                return topPanelView.topicIndex(threadId: threadId)
            } else {
                return nil
            }
        }
    }
    
    public func makeView() -> View {
        return View(frame: CGRect())
    }
    
    public func update(view: View, availableSize: CGSize, state: EmptyComponentState, environment: Environment<ChatSidePanelEnvironment>, transition: ComponentTransition) -> CGSize {
        return view.update(component: self, availableSize: availableSize, state: state, environment: environment, transition: transition)
    }
}

public final class ChatTopicsHeaderPanelComponent: Component {
    public enum Location {
        case top
        case bottom
    }
    
    public let context: AccountContext
    public let theme: PresentationTheme
    public let strings: PresentationStrings
    public let peerId: EnginePeer.Id
    public let kind: ChatSideTopicsPanel.Kind
    public let location: Location
    public let topicId: Int64?
    public let controller: () -> ViewController?
    public let togglePanel: () -> Void
    public let updateTopicId: (Int64?, ChatControllerAnimateInnerChatSwitchDirection) -> Void
    public let openDeletePeer: (Int64) -> Void
    public let roll: ChatTopicsRollState?
    public let rollActions: ChatTopicsRollActions?
    /// Rolled up, the island shares its row with the panel after it (the pinned message), which becomes the wheel's
    /// display.
    public let sharesRowWithNextPanel: Bool
    /// The height of the island's row: the pinned message's when it shares its row.
    public let rollRowHeight: CGFloat
    
    public init(
        context: AccountContext,
        theme: PresentationTheme,
        strings: PresentationStrings,
        peerId: EnginePeer.Id,
        kind: ChatSideTopicsPanel.Kind,
        location: Location,
        topicId: Int64?,
        controller: @escaping () -> ViewController?,
        togglePanel: @escaping () -> Void,
        updateTopicId: @escaping (Int64?, ChatControllerAnimateInnerChatSwitchDirection) -> Void,
        openDeletePeer: @escaping (Int64) -> Void,
        roll: ChatTopicsRollState? = nil,
        rollActions: ChatTopicsRollActions? = nil,
        sharesRowWithNextPanel: Bool = false,
        rollRowHeight: CGFloat = 48.0
    ) {
        self.context = context
        self.theme = theme
        self.strings = strings
        self.peerId = peerId
        self.kind = kind
        self.location = location
        self.topicId = topicId
        self.controller = controller
        self.togglePanel = togglePanel
        self.updateTopicId = updateTopicId
        self.openDeletePeer = openDeletePeer
        self.roll = roll
        self.rollActions = rollActions
        self.sharesRowWithNextPanel = sharesRowWithNextPanel
        self.rollRowHeight = rollRowHeight
    }
    
    public static func ==(lhs: ChatTopicsHeaderPanelComponent, rhs: ChatTopicsHeaderPanelComponent) -> Bool {
        if lhs.context !== rhs.context {
            return false
        }
        if lhs.theme !== rhs.theme {
            return false
        }
        if lhs.strings !== rhs.strings {
            return false
        }
        if lhs.peerId != rhs.peerId {
            return false
        }
        if lhs.kind != rhs.kind {
            return false
        }
        if lhs.location != rhs.location {
            return false
        }
        if lhs.topicId != rhs.topicId {
            return false
        }
        if lhs.roll != rhs.roll {
            return false
        }
        if lhs.rollActions !== rhs.rollActions {
            return false
        }
        if lhs.sharesRowWithNextPanel != rhs.sharesRowWithNextPanel {
            return false
        }
        if lhs.rollRowHeight != rhs.rollRowHeight {
            return false
        }
        return true
    }
    
    /// The bar at the top or the bottom. Rolled up, it is the wheel turning in a well in its own glass island, at
    /// the start of the pinned message's row at the top (one island, like a radio: the wheel is the dial and the
    /// pinned message its display) or at the start of the bar's own row at the bottom. Tapped, the wheel rolls along
    /// the row like a carpet with the tabs laid down behind it.
    public final class View: UIView, HeaderPanelContainerFlexiblePanelView {
        private let tabsClipView: UIView
        private let trackView: UIImageView
        private let panel = ComponentView<ChatSidePanelEnvironment>()
        private let gearLineView: UIImageView
        private var wheelView: ChatTopicsRollWheelView?
        private var displayView: ChatTopicsRollDisplayView?
        private let display = ChatTopicsRollDisplayState()
        private var rollItems: [ChatTopicsRollItem] = []
        private var beltAnimator: DisplayLinkAnimator?
        private var gearTheme: PresentationTheme?
        private var trackTheme: PresentationTheme?
        
        private var component: ChatTopicsHeaderPanelComponent?
        private weak var state: EmptyComponentState?
        private var isUpdating = false
        
        /// While the wheel turns, its display takes over the row it shares with the pinned message.
        public var coversNextPanel: Bool {
            guard let component = self.component, component.sharesRowWithNextPanel, component.roll?.showsWheel == true else {
                return false
            }
            return self.display.item != nil
        }
        
        override public init(frame: CGRect) {
            self.tabsClipView = UIView()
            self.tabsClipView.clipsToBounds = true
            self.trackView = UIImageView()
            self.trackView.isUserInteractionEnabled = false
            self.gearLineView = UIImageView()
            self.gearLineView.isUserInteractionEnabled = false
            self.gearLineView.alpha = 0.0
            
            super.init(frame: frame)
            
            self.addSubview(self.tabsClipView)
            self.tabsClipView.addSubview(self.trackView)
            self.tabsClipView.addSubview(self.gearLineView)
            
            self.display.shownUpdated = { [weak self] in
                guard let self, !self.isUpdating else {
                    return
                }
                // The container lays out again: the pinned message's words go at once, before the display's come in,
                // or the island stretches out to show it.
                let sharesRow = self.component?.sharesRowWithNextPanel ?? false
                self.state?.updated(transition: sharesRow ? .easeInOut(duration: 0.12) : .spring(duration: 0.4))
            }
            self.display.contentUpdated = { [weak self] in
                self?.updateDisplayContent(animated: true)
            }
        }
        
        required public init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }
        
        override public func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
            guard let result = super.hitTest(point, with: event) else {
                return nil
            }
            // Where the island shares its row, the rest of the row is the pinned message's.
            if result === self {
                return nil
            }
            return result
        }
        
        func update(component: ChatTopicsHeaderPanelComponent, availableSize: CGSize, state: EmptyComponentState, environment: Environment<Empty>, transition: ComponentTransition) -> CGSize {
            self.isUpdating = true
            defer {
                self.isUpdating = false
            }
            
            let previousComponent = self.component
            self.component = component
            self.state = state
            
            let tabsSize = CGSize(width: availableSize.width, height: 44.0)
            let roll = component.roll
            let showsWheel = roll?.showsWheel ?? false
            let wasShowingWheel = previousComponent?.roll?.showsWheel ?? showsWheel
            let isRollTransition = previousComponent != nil && wasShowingWheel != showsWheel && !transition.animation.isImmediate
            
            var rollControl: ChatTopicsRollControlView.Mode?
            if let roll {
                rollControl = roll.isRolledUp ? .keepOpen : .windUp
            }
            
            let _ = self.panel.update(
                transition: transition,
                component: AnyComponent(ChatSideTopicsPanel(
                    context: component.context,
                    theme: component.theme,
                    strings: component.strings,
                    location: component.location == .top ? .top : .bottom,
                    peerId: component.peerId,
                    kind: component.kind,
                    topicId: component.topicId,
                    controller: component.controller,
                    togglePanel: component.togglePanel,
                    updateTopicId: { [weak self] threadId, direction in
                        guard let self, let component = self.component else {
                            return
                        }
                        component.updateTopicId(threadId, direction ? .right : .left)
                        self.windUpAfterPicking()
                    },
                    openDeletePeer: component.openDeletePeer,
                    rollControl: rollControl,
                    rollControlAction: rollControl == nil ? nil : { [weak self] in
                        guard let self, let component = self.component, let roll = component.roll else {
                            return
                        }
                        if roll.isRolledUp {
                            component.rollActions?.keepOpen()
                        } else {
                            component.rollActions?.windUp()
                        }
                    }
                )),
                environment: {
                    ChatSidePanelEnvironment(insets: UIEdgeInsets(
                        top: 0.0,
                        left: 0.0,
                        bottom: 0.0,
                        right: 0.0
                    ))
                },
                containerSize: CGSize(width: tabsSize.width + 28.0, height: 48.0)
            )
            let panelFrame = CGRect(origin: CGPoint(x: -20.0, y: -5.0), size: CGSize(width: tabsSize.width + 28.0, height: 48.0))
            if let panelView = self.panel.view as? ChatSideTopicsPanel.View {
                if panelView.superview == nil {
                    panelView.clipsToBounds = true
                    self.tabsClipView.insertSubview(panelView, belowSubview: self.gearLineView)
                    panelView.itemsUpdated = { [weak self, weak panelView] items in
                        self?.rawItemsUpdated(items, typing: panelView?.typing ?? [:])
                    }
                    panelView.typingUpdated = { [weak self, weak panelView] typing in
                        guard let panelView else {
                            return
                        }
                        self?.rawItemsUpdated(panelView.items, typing: typing)
                    }
                }
                transition.setFrame(view: panelView, frame: panelFrame)
            }
            
            // The tabs lie on the wheel's track, opened out along the bar, the toggle and the far end's control in the
            // drum's shaded ends. The rolled-out wheel waits on it, in its own well at the far end.
            if self.trackTheme !== component.theme {
                self.trackTheme = component.theme
                self.trackView.image = ChatTopicsRollDrawing.generateTrackImage(theme: component.theme, cornerRadius: 18.0, isVertical: false)
            }
            let trackFrame = CGRect(x: 4.0, y: floor((tabsSize.height - 36.0) * 0.5), width: max(0.0, tabsSize.width - 8.0), height: 36.0)
            if self.trackView.bounds.isEmpty {
                self.trackView.frame = trackFrame
            } else {
                transition.setFrame(view: self.trackView, frame: trackFrame)
            }
            
            guard let roll else {
                // A bar that does not roll is the stock bar.
                transition.setFrame(view: self.tabsClipView, frame: CGRect(origin: CGPoint(), size: tabsSize))
                self.tabsClipView.alpha = 1.0
                return tabsSize
            }
            let _ = roll
            
            let rowHeight = component.rollRowHeight
            let islandInset = ChatTopicsRollIsland.inset(rowHeight: rowHeight)
            let islandWidth = ChatTopicsRollIsland.width(rowHeight: rowHeight)
            let wheelSize = CGSize(width: ChatTopicsRollWheelView.length, height: ChatTopicsRollWheelView.thickness)
            
            // The wheel. Rolled out, it waits at the bar's far end, where it fades as the bar lands; winding up, it
            // rolls back along the row gathering the tabs, into its well at the start of the island.
            let wheelView: ChatTopicsRollWheelView
            var wheelTransition = transition
            if let current = self.wheelView {
                wheelView = current
            } else {
                wheelTransition = .immediate
                wheelView = ChatTopicsRollWheelView()
                self.wheelView = wheelView
                self.addSubview(wheelView)
                wheelView.tapped = { [weak self] in
                    self?.component?.rollActions?.rollOut()
                }
                wheelView.longPressed = { [weak self] in
                    // The bar's tabs carry each topic's options.
                    self?.component?.rollActions?.rollOut()
                }
                wheelView.turned = { [weak self] item, direction in
                    guard let self, let component = self.component else {
                        return
                    }
                    component.updateTopicId(item.id, direction > 0 ? .right : .left)
                }
                wheelView.scrubbed = { [weak self] item, direction in
                    self?.display.scrubbed(item: item, direction: direction)
                }
                wheelView.alpha = showsWheel ? 1.0 : 0.0
            }
            if self.rollItems.isEmpty, let panelView = self.panel.view as? ChatSideTopicsPanel.View, !panelView.items.isEmpty {
                self.rollItems = ChatTopicsRollItem.makeItems(context: component.context, kind: component.kind, rawItems: panelView.items, typing: panelView.typing)
            }
            wheelView.update(context: component.context, theme: component.theme, strings: component.strings, items: self.rollItems, currentId: component.topicId, isVertical: false, animated: !transition.animation.isImmediate)
            
            let wheelFrame: CGRect
            let tabsClipFrame: CGRect
            if showsWheel {
                wheelFrame = CGRect(origin: CGPoint(x: islandInset, y: islandInset), size: wheelSize)
                // The tabs are gathered under the wheel.
                tabsClipFrame = CGRect(origin: CGPoint(), size: CGSize(width: wheelFrame.midX, height: tabsSize.height))
            } else {
                wheelFrame = CGRect(origin: CGPoint(x: tabsSize.width - wheelSize.width - 4.0, y: floor((tabsSize.height - wheelSize.height) * 0.5)), size: wheelSize)
                tabsClipFrame = CGRect(origin: CGPoint(), size: tabsSize)
            }
            wheelTransition.setFrame(view: wheelView, frame: wheelFrame)
            transition.setFrame(view: self.tabsClipView, frame: tabsClipFrame)
            wheelView.isUserInteractionEnabled = showsWheel
            wheelView.accessibilityElementsHidden = !showsWheel
            self.tabsClipView.accessibilityElementsHidden = showsWheel
            
            if self.gearTheme !== component.theme {
                self.gearTheme = component.theme
                self.gearLineView.image = ChatTopicsRollDrawing.generateGearLineImage(theme: component.theme, isVertical: false)?.resizableImage(withCapInsets: UIEdgeInsets(), resizingMode: .tile)
            }
            // The gear line runs along the track's lower edge, where the wheel's own ticks are.
            self.gearLineView.frame = CGRect(x: 12.0, y: trackFrame.maxY - 3.0 - 3.5, width: max(0.0, tabsSize.width - 24.0), height: 3.5)
            
            if isRollTransition, let duration = transitionDuration(transition) {
                if showsWheel {
                    ComponentTransition.easeInOut(duration: 0.15).setAlpha(view: wheelView, alpha: 1.0)
                    ComponentTransition.easeInOut(duration: duration * 0.3).setAlpha(view: self.tabsClipView, alpha: 0.0, delay: duration * 0.7)
                } else {
                    ComponentTransition.easeInOut(duration: duration * 0.4).setAlpha(view: wheelView, alpha: 0.0, delay: duration * 0.6)
                    ComponentTransition.easeInOut(duration: 0.15).setAlpha(view: self.tabsClipView, alpha: 1.0)
                }
                animateGearLine(view: self.gearLineView, windingUp: showsWheel, duration: duration)
                self.beltAnimator?.invalidate()
                self.beltAnimator = animateRollBelt(wheelView: wheelView, itemCount: self.rollItems.count, windingUp: showsWheel, duration: duration)
            } else if !isRollTransition {
                wheelView.alpha = showsWheel ? 1.0 : 0.0
                self.tabsClipView.alpha = showsWheel ? 0.0 : 1.0
            }
            
            if !showsWheel {
                self.display.reset()
            }
            let displayItem = showsWheel ? self.display.item : nil
            
            // The display: over the pinned message's row, or in the island stretched out to show it.
            let displayLeft = ChatTopicsRollIsland.sharedPanelLeftInset(rowHeight: rowHeight)
            let displayFrame = CGRect(x: displayLeft, y: 0.0, width: max(0.0, tabsSize.width - displayLeft), height: rowHeight)
            if displayItem != nil || self.displayView != nil {
                let displayView: ChatTopicsRollDisplayView
                if let current = self.displayView {
                    displayView = current
                } else {
                    displayView = ChatTopicsRollDisplayView()
                    self.displayView = displayView
                    self.insertSubview(displayView, belowSubview: wheelView)
                    displayView.alpha = 0.0
                }
                displayView.frame = displayFrame
                transition.setAlpha(view: displayView, alpha: displayItem != nil ? 1.0 : 0.0)
                self.updateDisplayContent(animated: !transition.animation.isImmediate)
            }
            
            if showsWheel {
                if component.sharesRowWithNextPanel || displayItem != nil {
                    return CGSize(width: tabsSize.width, height: rowHeight)
                } else {
                    return CGSize(width: islandWidth, height: rowHeight)
                }
            } else {
                return tabsSize
            }
        }
        
        private func updateDisplayContent(animated: Bool) {
            guard let component = self.component, let displayView = self.displayView else {
                return
            }
            let item = (component.roll?.showsWheel ?? false) ? self.display.item : nil
            displayView.update(item: item, newsTitles: self.wheelView?.newsTitles(max: 3), direction: self.display.direction, theme: component.theme, strings: component.strings, style: component.location == .bottom ? .compact : .row, size: displayView.bounds.size, animated: animated)
        }
        
        private func rawItemsUpdated(_ items: [EngineChatList.Item], typing: [Int64: String]) {
            guard let component = self.component, let wheelView = self.wheelView else {
                return
            }
            self.rollItems = ChatTopicsRollItem.makeItems(context: component.context, kind: component.kind, rawItems: items, typing: typing)
            wheelView.update(context: component.context, theme: component.theme, strings: component.strings, items: self.rollItems, currentId: component.topicId, isVertical: false, animated: true)
            self.display.itemsUpdated(self.rollItems)
        }
        
        // Picking a topic from a bar that is out for a moment winds it back up 0.34 s later.
        private func windUpAfterPicking() {
            guard let component = self.component, let roll = component.roll, roll.isTemporarilyOut else {
                return
            }
            let rollActions = component.rollActions
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.34, execute: {
                rollActions?.windUp()
            })
        }
        
        public func topicIndex(threadId: Int64?) -> Int? {
            if let panelView = self.panel.view as? ChatSideTopicsPanel.View {
                return panelView.topicIndex(threadId: threadId)
            } else {
                return nil
            }
        }
    }
    
    public func makeView() -> View {
        return View(frame: CGRect())
    }
    
    public func update(view: View, availableSize: CGSize, state: EmptyComponentState, environment: Environment<Empty>, transition: ComponentTransition) -> CGSize {
        return view.update(component: self, availableSize: availableSize, state: state, environment: environment, transition: transition)
    }
}
