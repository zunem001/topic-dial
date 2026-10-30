import Foundation
import UIKit
import Display
import ComponentFlow
import TelegramPresentationData
import GlassBackgroundComponent

public protocol HeaderPanelContainerChildView: UIView {
    func setOverlayContainerView(overlayContainerView: UIView)
}

/// A panel that changes on its own, like the topic wheel: its own state updates lay the whole container out
/// again, so the glass follows its size, and it can cover the panel that shares its row.
public protocol HeaderPanelContainerFlexiblePanelView: UIView {
    /// The panel shows something of its own over the panel that shares its row, so that panel's content steps
    /// aside (the wheel's display over the pinned message).
    var coversNextPanel: Bool { get }
}

public final class HeaderPanelContainerComponent: Component {
    public final class Panel: Equatable {
        public let key: AnyHashable
        public let orderIndex: Int
        public let component: AnyComponent<Empty>
        /// The panel sits at the start of the next panel's row instead of taking a row of its own; the next
        /// panel leaves room for it.
        public let sharesRowWithNextPanel: Bool
        
        public init(key: AnyHashable, orderIndex: Int, component: AnyComponent<Empty>, sharesRowWithNextPanel: Bool = false) {
            self.key = key
            self.orderIndex = orderIndex
            self.component = component
            self.sharesRowWithNextPanel = sharesRowWithNextPanel
        }
        
        public static func ==(lhs: Panel, rhs: Panel) -> Bool {
            if lhs.key != rhs.key {
                return false
            }
            if lhs.orderIndex != rhs.orderIndex {
                return false
            }
            if lhs.component != rhs.component {
                return false
            }
            if lhs.sharesRowWithNextPanel != rhs.sharesRowWithNextPanel {
                return false
            }
            return true
        }
    }
    
    public let theme: PresentationTheme
    public let preferClearGlass: Bool
    public let tabs: AnyComponent<Empty>?
    public let panels: [Panel]
    
    public init(
        theme: PresentationTheme,
        preferClearGlass: Bool = false,
        tabs: AnyComponent<Empty>?,
        panels: [Panel]
    ) {
        self.theme = theme
        self.preferClearGlass = preferClearGlass
        self.tabs = tabs
        self.panels = panels
    }
    
    public static func ==(lhs: HeaderPanelContainerComponent, rhs: HeaderPanelContainerComponent) -> Bool {
        if lhs.theme !== rhs.theme {
            return false
        }
        if lhs.preferClearGlass != rhs.preferClearGlass {
            return false
        }
        if lhs.tabs != rhs.tabs {
            return false
        }
        if lhs.panels != rhs.panels {
            return false
        }
        return true
    }
    
    private final class PanelItemView: UIView {
        let view = ComponentView<Empty>()
        let separator = SimpleLayer()
        
        override init(frame: CGRect) {
            super.init(frame: frame)
        }
        
        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }
        
        override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
            // Where the panel lets a touch through (a panel sharing its row with the next one), so does its item.
            let result = super.hitTest(point, with: event)
            if result === self {
                return nil
            }
            return result
        }
    }
    
    public final class View: UIView {
        private let backgroundContainer: GlassBackgroundContainerView
        private let backgroundView: GlassBackgroundView
        private let contentContainer: UIView
        
        private var tabsView: ComponentView<Empty>?
        private var panelViews: [AnyHashable: PanelItemView] = [:]
        
        private var component: HeaderPanelContainerComponent?
        private weak var state: EmptyComponentState?
        
        public var tabs: UIView? {
            return self.tabsView?.view
        }
        
        public func panel(forKey key: AnyHashable) -> UIView? {
            return self.panelViews[key]?.view.view
        }
        
        override init(frame: CGRect) {
            self.backgroundContainer = GlassBackgroundContainerView()
            self.backgroundView = GlassBackgroundView()
            self.contentContainer = UIView()
            self.contentContainer.clipsToBounds = true
            
            super.init(frame: frame)
            
            self.backgroundContainer.contentView.addSubview(self.backgroundView)
            self.addSubview(self.backgroundContainer)
            
            self.backgroundView.contentView.addSubview(self.contentContainer)
        }
        
        required public init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }
        
        func update(component: HeaderPanelContainerComponent, availableSize: CGSize, state: EmptyComponentState, environment: Environment<Empty>, transition: ComponentTransition) -> CGSize {
            var isAnimatingReplacement = false
            if let previousComponent = self.component {
                isAnimatingReplacement = !component.panels.contains(where: { panel in previousComponent.panels.contains(where: { $0.key == panel.key }) })
            }
            
            self.component = component
            self.state = state
            
            let sideInset: CGFloat = 16.0
            
            var size = CGSize(width: availableSize.width, height: 0.0)
            
            var isFirstPanel = true
            
            if let tabs = component.tabs {
                let tabsView: ComponentView<Empty>
                var tabsTransition = transition
                if let current = self.tabsView {
                    tabsView = current
                } else {
                    tabsTransition = tabsTransition.withAnimation(.none)
                    tabsView = ComponentView()
                    self.tabsView = tabsView
                }
                let tabsSize = tabsView.update(
                    transition: tabsTransition,
                    component: tabs,
                    environment: {},
                    containerSize: CGSize(width: availableSize.width - sideInset * 2.0, height: 40.0)
                )
                let tabsFrame = CGRect(origin: CGPoint(x: 0.0, y: size.height), size: tabsSize)
                if let tabsComponentView = tabsView.view {
                    if tabsComponentView.superview == nil {
                        self.contentContainer.addSubview(tabsComponentView)
                        if let tabsComponentView = tabsComponentView as? HeaderPanelContainerChildView {
                            tabsComponentView.setOverlayContainerView(overlayContainerView: self.backgroundContainer.contentView)
                        }
                        transition.animateAlpha(view: tabsComponentView, from: 0.0, to: 1.0)
                    }
                    tabsTransition.setFrame(view: tabsComponentView, frame: tabsFrame)
                }
                size.height += tabsSize.height
                isFirstPanel = false
            } else if let tabsView = self.tabsView {
                self.tabsView = nil
                if let tabsComponentView = tabsView.view {
                    transition.setAlpha(view: tabsComponentView, alpha: 0.0, completion: { [weak tabsComponentView] _ in
                        tabsComponentView?.removeFromSuperview()
                    })
                }
            }
            
            var validPanelKeys: [AnyHashable] = []
            var rowCount = component.tabs != nil ? 1 : 0
            var maxPanelWidth: CGFloat = 0.0
            var allPanelsAreFlexible = !component.panels.isEmpty && component.tabs == nil
            // A panel that shares its row with the next one: the row's height so far, and whether it covers the
            // next panel's content.
            var sharedRow: (height: CGFloat, coversNextPanel: Bool)?
            for i in 0 ..< component.panels.count {
                let panel = component.panels[i]
                validPanelKeys.append(panel.key)
                let sharesRow = panel.sharesRowWithNextPanel && i + 1 < component.panels.count
                
                var panelTransition = transition
                let panelView: PanelItemView
                if let current = self.panelViews[panel.key] {
                    panelView = current
                } else {
                    panelTransition = panelTransition.withAnimation(.none)
                    panelView = PanelItemView()
                    self.panelViews[panel.key] = panelView
                    self.contentContainer.layer.insertSublayer(panelView.separator, at: 0)
                    self.contentContainer.addSubview(panelView)
                }
                
                let panelSize = panelView.view.update(
                    transition: panelTransition,
                    component: panel.component,
                    environment: {},
                    containerSize: CGSize(width: availableSize.width - sideInset * 2.0, height: 10000.0)
                )
                maxPanelWidth = max(maxPanelWidth, panelSize.width)
                
                let flexiblePanelView = panelView.view.view as? HeaderPanelContainerFlexiblePanelView
                if flexiblePanelView != nil {
                    panelView.view.parentState = state
                } else {
                    allPanelsAreFlexible = false
                }
                
                let panelFrame = CGRect(origin: CGPoint(x: 0.0, y: size.height), size: panelSize)
                if let panelComponentView = panelView.view.view {
                    if panelComponentView.superview == nil {
                        panelView.addSubview(panelComponentView)
                        transition.animateAlpha(view: panelView, from: 0.0, to: 1.0)
                        panelView.separator.opacity = 0.0
                        panelView.clipsToBounds = true
                        if isAnimatingReplacement {
                            panelView.frame = panelFrame
                        } else {
                            panelView.frame = CGRect(origin: panelFrame.origin, size: CGSize(width: panelFrame.width, height: 0.0))
                        }
                    }
                    
                    panelView.separator.backgroundColor = component.theme.list.itemPlainSeparatorColor.cgColor
                    
                    let isFrameUpdated = panelComponentView.frame != panelFrame
                    transition.setFrame(view: panelView, frame: panelFrame, completion: { [weak panelView] completed in
                        if let panelView, completed, isFrameUpdated {
                            panelView.clipsToBounds = false
                        }
                    })
                    panelTransition.setFrame(view: panelComponentView, frame: CGRect(origin: CGPoint(), size: panelFrame.size))
                    panelTransition.setFrame(layer: panelView.separator, frame: CGRect(origin: panelFrame.origin, size: CGSize(width: panelFrame.width, height: UIScreenPixel)))
                    
                    // The second panel of a shared row is in the row the first one started: no line between them.
                    transition.setAlpha(layer: panelView.separator, alpha: (isFirstPanel || sharedRow != nil) ? 0.0 : 1.0)
                    // While the panel that shares its row shows something over it, this one's content steps aside.
                    let isCovered = sharedRow?.coversNextPanel ?? false
                    if panelComponentView.alpha != (isCovered ? 0.0 : 1.0) {
                        transition.setAlpha(view: panelComponentView, alpha: isCovered ? 0.0 : 1.0)
                    }
                }
                if sharesRow {
                    // It sits over the start of the next panel's row, in front of it.
                    self.contentContainer.bringSubviewToFront(panelView)
                    sharedRow = (panelSize.height, flexiblePanelView?.coversNextPanel ?? false)
                } else {
                    size.height += max(panelSize.height, sharedRow?.height ?? 0.0)
                    sharedRow = nil
                    rowCount += 1
                }
                isFirstPanel = false
            }
            
            var removedPanelKeys: [AnyHashable] = []
            for (key, panelView) in self.panelViews {
                if !validPanelKeys.contains(key) {
                    removedPanelKeys.append(key)
                    transition.setAlpha(view: panelView, alpha: 0.0, completion: { [weak panelView] _ in
                        panelView?.removeFromSuperview()
                    })
                    let separator = panelView.separator
                    transition.setAlpha(layer: separator, alpha: 0.0, completion: { [weak separator] _ in
                        separator?.removeFromSuperlayer()
                    })
                    if !isAnimatingReplacement {
                        panelView.clipsToBounds = true
                        transition.setFrame(view: panelView, frame: CGRect(origin: panelView.frame.origin, size: CGSize(width: panelView.bounds.width, height: 0.0)))
                    }
                }
            }
            for key in removedPanelKeys {
                self.panelViews.removeValue(forKey: key)
            }
            
            let backgroundSize = CGSize(width: size.width, height: max(40.0, size.height))
            
            transition.setFrame(view: self.backgroundContainer, frame: CGRect(origin: CGPoint(), size: backgroundSize).insetBy(dx: -32.0, dy: -32.0))
            self.backgroundContainer.update(size: CGSize(width: backgroundSize.width + 32.0 * 2.0, height: backgroundSize.height + 32.0 * 2.0), isDark: component.theme.overallDarkAppearance, transition: transition)
            
            // Panels that size themselves (the topic wheel alone, its island) take only the glass they need, from
            // the leading edge; any other panel spans the row.
            var backgroundWidth = size.width - sideInset * 2.0
            if allPanelsAreFlexible {
                backgroundWidth = min(backgroundWidth, maxPanelWidth)
            }
            let backgroundFrame = CGRect(origin: CGPoint(x: 32.0 + sideInset, y: 32.0), size: CGSize(width: backgroundWidth, height: backgroundSize.height))
            
            var cornerRadius: CGFloat = 0.0
            if rowCount == 1 && backgroundFrame.height <= 50.0 {
                cornerRadius = backgroundFrame.height * 0.5
            } else {
                cornerRadius = 20.0
            }
            
            transition.setFrame(view: self.backgroundView, frame: backgroundFrame)
            self.backgroundView.update(size: backgroundFrame.size, cornerRadius: cornerRadius, isDark: component.theme.overallDarkAppearance, tintColor: .init(kind: component.preferClearGlass ? .clear : .panel), isInteractive: true, transition: transition)
            
            transition.setAlpha(view: self.backgroundContainer, alpha: (component.tabs != nil || !component.panels.isEmpty) ? 1.0 : 0.0)
            
            transition.setFrame(view: self.contentContainer, frame: CGRect(origin: CGPoint(), size: backgroundFrame.size))
            
            transition.setCornerRadius(layer: self.contentContainer.layer, cornerRadius: min(cornerRadius, backgroundFrame.height * 0.5))
            
            return size
        }
    }
    
    public func makeView() -> View {
        return View(frame: CGRect())
    }
    
    public func update(view: View, availableSize: CGSize, state: EmptyComponentState, environment: Environment<Empty>, transition: ComponentTransition) -> CGSize {
        return view.update(component: self, availableSize: availableSize, state: state, environment: environment, transition: transition)
    }
}
