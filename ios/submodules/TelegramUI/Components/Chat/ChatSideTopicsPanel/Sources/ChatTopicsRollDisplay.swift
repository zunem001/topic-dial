import Foundation
import UIKit
import Display
import ComponentFlow
import TelegramPresentationData

/// The wheel's display. While the wheel turns, the pinned message steps aside and the display tunes to the topic
/// under the lens: its name and its unread counter (or @) on the first line, its latest message as
/// "Sender: text" on the second (or who is typing there), on the pinned message's own two lines and behind the
/// same line. For All it lists the topics with news. Each topic slides in from the side the wheel turns to.
public final class ChatTopicsRollDisplayView: UIView {
    public enum Style: Equatable {
        /// The pinned message's own row: its line, a 15 pt title and a 15 pt text line.
        case row
        /// The bottom bar's row: two smaller lines, no line.
        case compact
    }
    
    private final class Lines {
        let titleLabel: UILabel
        let textLabel: UILabel
        // What the words say, so a topic that changes while it shows only rewrites them when they change.
        var item: ChatTopicsRollItem?
        var newsTitles: String?
        var theme: PresentationTheme?
        var style: Style?
        
        init() {
            self.titleLabel = UILabel()
            self.titleLabel.lineBreakMode = .byTruncatingTail
            self.textLabel = UILabel()
            self.textLabel.lineBreakMode = .byTruncatingTail
        }
        
        func removeFromSuperview() {
            self.titleLabel.removeFromSuperview()
            self.textLabel.removeFromSuperview()
        }
    }
    
    private let lineView: UIImageView
    private var lines: Lines?
    private var currentItemId: Int64??
    private var style: Style = .row
    private var lineColor: UIColor?
    
    override public init(frame: CGRect) {
        self.lineView = UIImageView()
        
        super.init(frame: frame)
        
        self.isUserInteractionEnabled = false
        self.clipsToBounds = true
        self.addSubview(self.lineView)
    }
    
    required public init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    /// Where the text starts, after the line, the way the pinned message lays out its own.
    public static func textInset(style: Style) -> CGFloat {
        switch style {
        case .row:
            return 18.0 + 10.0
        case .compact:
            return 12.0
        }
    }
    
    /// Shows `item` (or nothing) in `size`. `direction` is the way the wheel turned to it: the new lines slide in
    /// from that side and the old ones leave toward the other.
    public func update(item: ChatTopicsRollItem?, newsTitles: String?, direction: Int, theme: PresentationTheme, strings: PresentationStrings, style: Style, size: CGSize, animated: Bool) {
        self.style = style
        
        let lineColor = theme.chat.inputPanel.panelControlColor
        if self.lineColor != lineColor || self.lineView.image == nil {
            self.lineColor = lineColor
            self.lineView.image = generateStretchableFilledCircleImage(diameter: 2.0, color: lineColor)
        }
        self.lineView.isHidden = style != .row
        self.lineView.frame = CGRect(x: 18.0, y: 8.0, width: 2.0, height: max(0.0, size.height - 16.0))
        
        // nil when nothing is shown, .some(nil) for All.
        let itemId: Int64?? = item.map { $0.id }
        if let lines = self.lines, itemId == self.currentItemId {
            // The same topic: its words follow what happens there (news, someone typing) in place.
            if let item {
                self.setText(lines: lines, item: item, newsTitles: newsTitles, theme: theme, strings: strings)
            }
            self.layout(lines: lines, size: size)
            return
        }
        self.currentItemId = itemId
        
        let previous = self.lines
        self.lines = nil
        let offset = CGFloat(direction) * 14.0
        if let previous {
            if animated {
                let leaveOffset = -offset
                UIView.animate(withDuration: 0.12, delay: 0.0, options: [.curveEaseIn, .beginFromCurrentState], animations: {
                    previous.titleLabel.alpha = 0.0
                    previous.textLabel.alpha = 0.0
                    previous.titleLabel.transform = CGAffineTransform(translationX: leaveOffset, y: 0.0)
                    previous.textLabel.transform = CGAffineTransform(translationX: leaveOffset, y: 0.0)
                }, completion: { _ in
                    previous.removeFromSuperview()
                })
            } else {
                previous.removeFromSuperview()
            }
        }
        
        guard let item else {
            return
        }
        
        let lines = Lines()
        self.lines = lines
        self.setText(lines: lines, item: item, newsTitles: newsTitles, theme: theme, strings: strings)
        
        self.addSubview(lines.titleLabel)
        self.addSubview(lines.textLabel)
        self.layout(lines: lines, size: size)
        
        if animated {
            lines.titleLabel.alpha = 0.0
            lines.textLabel.alpha = 0.0
            lines.titleLabel.transform = CGAffineTransform(translationX: offset, y: 0.0)
            lines.textLabel.transform = CGAffineTransform(translationX: offset, y: 0.0)
            // The words come in once the old ones (or the pinned message's) have gone, so words never show over
            // other words.
            UIView.animate(withDuration: 0.22, delay: 0.12, options: [.curveEaseOut, .beginFromCurrentState], animations: {
                lines.titleLabel.alpha = 1.0
                lines.textLabel.alpha = 1.0
                lines.titleLabel.transform = .identity
                lines.textLabel.transform = .identity
            }, completion: nil)
        }
    }
    
    private func setText(lines: Lines, item: ChatTopicsRollItem, newsTitles: String?, theme: PresentationTheme, strings: PresentationStrings) {
        if let current = lines.item, current == item, lines.newsTitles == newsTitles, lines.theme === theme, lines.style == self.style {
            return
        }
        lines.item = item
        lines.newsTitles = newsTitles
        lines.theme = theme
        lines.style = self.style
        
        let titleFont: UIFont
        let textFont: UIFont
        switch self.style {
        case .row:
            titleFont = Font.medium(15.0)
            textFont = Font.regular(15.0)
        case .compact:
            titleFont = Font.medium(13.0)
            textFont = Font.regular(12.0)
        }
        
        let title = NSMutableAttributedString(string: item.title, font: titleFont, textColor: theme.chat.inputPanel.primaryTextColor)
        if item.hasMention || item.unreadCount > 0 {
            var counter = item.hasMention ? "@" : ""
            if item.unreadCount > 0 {
                counter += (counter.isEmpty ? "" : " ") + countString(Int64(item.unreadCount))
            }
            let counterColor = item.isMuted && !item.hasMention ? theme.chatList.unreadBadgeInactiveBackgroundColor : theme.chatList.unreadBadgeActiveBackgroundColor
            title.append(NSAttributedString(string: "  " + counter, font: titleFont, textColor: counterColor))
        }
        lines.titleLabel.attributedText = title
        
        let text = NSMutableAttributedString()
        if item.isAll {
            text.append(NSAttributedString(string: newsTitles ?? strings.Chat_TopicsRoll_NoNews, font: textFont, textColor: theme.chat.inputPanel.secondaryTextColor))
        } else if let typingText = item.typingText {
            // Someone typing there: the display says who, in the accent colour, as the chat's title does.
            text.append(NSAttributedString(string: typingText, font: textFont, textColor: theme.chat.inputPanel.panelControlAccentColor))
        } else if let messageText = item.lastMessageText {
            if let author = item.lastMessageAuthor {
                text.append(NSAttributedString(string: author + ": ", font: textFont, textColor: theme.chat.inputPanel.primaryTextColor))
            }
            text.append(NSAttributedString(string: messageText, font: textFont, textColor: theme.chat.inputPanel.secondaryTextColor))
        } else {
            text.append(NSAttributedString(string: strings.Conversation_EmptyPlaceholder, font: textFont, textColor: theme.chat.inputPanel.secondaryTextColor))
        }
        lines.textLabel.attributedText = text
    }
    
    private func layout(lines: Lines, size: CGSize) {
        let inset = ChatTopicsRollDisplayView.textInset(style: self.style)
        let width = max(1.0, size.width - inset - 12.0)
        let titleSize = lines.titleLabel.sizeThatFits(CGSize(width: width, height: 100.0))
        let textSize = lines.textLabel.sizeThatFits(CGSize(width: width, height: 100.0))
        let titleY: CGFloat
        let textY: CGFloat
        switch self.style {
        case .row:
            // The pinned message's own lines: its title 6 points from the top and its text at 23.
            titleY = 6.0
            textY = 23.0
        case .compact:
            let total = titleSize.height + textSize.height
            titleY = floor((size.height - total) * 0.5)
            textY = titleY + titleSize.height
        }
        lines.titleLabel.bounds = CGRect(origin: CGPoint(), size: CGSize(width: min(width, ceil(titleSize.width)), height: ceil(titleSize.height)))
        lines.titleLabel.center = CGPoint(x: inset + lines.titleLabel.bounds.width * 0.5, y: titleY + lines.titleLabel.bounds.height * 0.5)
        lines.textLabel.bounds = CGRect(origin: CGPoint(), size: CGSize(width: min(width, ceil(textSize.width)), height: ceil(textSize.height)))
        lines.textLabel.center = CGPoint(x: inset + lines.textLabel.bounds.width * 0.5, y: textY + lines.textLabel.bounds.height * 0.5)
    }
}

/// The control at the far end of a rolled-out bar: `«` winds the bar up into the wheel, and while the bar is only
/// out for a moment (opened from the wheel), "Keep open" keeps it out, the classic way. Keep open is a button in
/// the accent colour with a soft shadow of its own colour, so it reads as something to press and never as the
/// topic you are in.
public final class ChatTopicsRollControlView: UIView {
    public enum Mode: Equatable {
        case windUp
        case keepOpen
    }
    
    private let button: HighlightTrackingButton
    private let iconView: UIImageView
    private let backgroundView: UIImageView
    private let titleLabel: UILabel
    
    private var mode: Mode?
    private var isVertical = false
    private var theme: PresentationTheme?
    public var action: (() -> Void)?
    
    override public init(frame: CGRect) {
        self.button = HighlightTrackingButton()
        self.iconView = UIImageView()
        self.iconView.isUserInteractionEnabled = false
        self.backgroundView = UIImageView()
        self.backgroundView.isUserInteractionEnabled = false
        self.titleLabel = UILabel()
        self.titleLabel.isUserInteractionEnabled = false
        self.titleLabel.textAlignment = .center
        
        super.init(frame: frame)
        
        self.addSubview(self.button)
        self.button.addSubview(self.backgroundView)
        self.button.addSubview(self.iconView)
        self.button.addSubview(self.titleLabel)
        
        self.button.addTarget(self, action: #selector(self.pressed), for: .touchUpInside)
        self.button.highligthedChanged = { [weak self] highlighted in
            guard let self else {
                return
            }
            let transition: ComponentTransition = .easeInOut(duration: highlighted ? 0.1 : 0.2)
            transition.setScale(view: self.button, scale: highlighted ? 0.92 : 1.0)
        }
        self.button.isAccessibilityElement = true
        self.button.accessibilityTraits = [.button]
    }
    
    required public init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    @objc private func pressed() {
        self.action?()
    }
    
    /// The size the control needs for `mode`.
    public static func size(mode: Mode, isVertical: Bool, strings: PresentationStrings) -> CGSize {
        switch mode {
        case .windUp:
            return CGSize(width: 32.0, height: 32.0)
        case .keepOpen:
            let titleSize = (strings.Chat_TopicsRoll_KeepOpen as NSString).size(withAttributes: [.font: Font.semibold(isVertical ? 11.0 : 13.0)])
            if isVertical {
                return CGSize(width: 60.0, height: 28.0)
            } else {
                return CGSize(width: ceil(titleSize.width) + 24.0, height: 28.0)
            }
        }
    }
    
    public func update(mode: Mode, isVertical: Bool, theme: PresentationTheme, strings: PresentationStrings, size: CGSize, transition: ComponentTransition) {
        let previousMode = self.mode
        let themeUpdated = self.theme !== theme
        self.theme = theme
        
        if previousMode != mode || self.isVertical != isVertical || themeUpdated {
            self.mode = mode
            self.isVertical = isVertical
            
            switch mode {
            case .windUp:
                self.iconView.image = ChatTopicsRollControlView.generateWindUpIcon(color: theme.chat.inputPanel.panelControlColor, isVertical: isVertical)
                self.iconView.isHidden = false
                self.backgroundView.isHidden = true
                self.titleLabel.isHidden = true
                self.button.accessibilityLabel = strings.Chat_TopicsRoll_WindUp
            case .keepOpen:
                let accent = theme.list.itemCheckColors.fillColor
                self.backgroundView.image = generateImage(CGSize(width: 40.0, height: 40.0), rotatedContext: { size, context in
                    context.clear(CGRect(origin: CGPoint(), size: size))
                    // A soft shadow of its own colour under it.
                    context.setShadow(offset: CGSize(width: 0.0, height: 1.5), blur: 4.0, color: accent.withMultipliedAlpha(0.45).cgColor)
                    context.setFillColor(accent.cgColor)
                    context.addPath(UIBezierPath(roundedRect: CGRect(x: 6.0, y: 6.0, width: 28.0, height: 28.0), cornerRadius: 14.0).cgPath)
                    context.fillPath()
                })?.stretchableImage(withLeftCapWidth: 20, topCapHeight: 20)
                self.titleLabel.attributedText = NSAttributedString(string: strings.Chat_TopicsRoll_KeepOpen, font: Font.semibold(isVertical ? 11.0 : 13.0), textColor: theme.list.itemCheckColors.foregroundColor)
                self.titleLabel.adjustsFontSizeToFitWidth = true
                self.titleLabel.minimumScaleFactor = 0.7
                self.iconView.isHidden = true
                self.backgroundView.isHidden = false
                self.titleLabel.isHidden = false
                self.button.accessibilityLabel = strings.Chat_TopicsRoll_KeepOpen
            }
            
            if let previousMode, previousMode != mode, !transition.animation.isImmediate {
                transition.animateAlpha(view: self.button, from: 0.0, to: 1.0)
                transition.animateScale(view: self.button, from: 0.6, to: 1.0)
            }
        }
        
        let bounds = CGRect(origin: CGPoint(), size: size)
        self.button.bounds = bounds
        self.button.center = CGPoint(x: bounds.midX, y: bounds.midY)
        self.backgroundView.frame = bounds.insetBy(dx: -6.0, dy: -6.0)
        self.titleLabel.frame = bounds.insetBy(dx: 6.0, dy: 0.0)
        if let image = self.iconView.image {
            self.iconView.frame = CGRect(origin: CGPoint(x: floor((size.width - image.size.width) * 0.5), y: floor((size.height - image.size.height) * 0.5)), size: image.size)
        }
    }
    
    private static func generateWindUpIcon(color: UIColor, isVertical: Bool) -> UIImage? {
        return generateImage(CGSize(width: 18.0, height: 18.0), rotatedContext: { size, context in
            context.clear(CGRect(origin: CGPoint(), size: size))
            context.setStrokeColor(color.cgColor)
            context.setLineWidth(1.66)
            context.setLineCap(.round)
            context.setLineJoin(.round)
            if isVertical {
                // ︽: winds a rail up into the wheel at its top.
                for i in 0 ..< 2 {
                    let y = 6.0 + CGFloat(i) * 5.0
                    context.move(to: CGPoint(x: 4.5, y: y + 4.0))
                    context.addLine(to: CGPoint(x: 9.0, y: y))
                    context.addLine(to: CGPoint(x: 13.5, y: y + 4.0))
                }
            } else {
                // «: winds a bar up into the wheel at its start.
                for i in 0 ..< 2 {
                    let x = 6.0 + CGFloat(i) * 5.0
                    context.move(to: CGPoint(x: x + 4.0, y: 4.5))
                    context.addLine(to: CGPoint(x: x, y: 9.0))
                    context.addLine(to: CGPoint(x: x + 4.0, y: 13.5))
                }
            }
            context.strokePath()
        })
    }
}
