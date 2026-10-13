enum StoryAudience {
  all,
  newUsers,
  hasCard,
  noCard,
  hasBankAccount,
  noBankAccount;

  static StoryAudience fromJson(String? val) {
    switch (val?.toUpperCase()) {
      case 'NEW_USERS':
        return StoryAudience.newUsers;
      case 'HAS_CARD':
        return StoryAudience.hasCard;
      case 'NO_CARD':
        return StoryAudience.noCard;
      case 'HAS_BANK_ACCOUNT':
        return StoryAudience.hasBankAccount;
      case 'NO_BANK_ACCOUNT':
        return StoryAudience.noBankAccount;
      case 'ALL':
      default:
        return StoryAudience.all;
    }
  }

  String toJson() {
    switch (this) {
      case StoryAudience.newUsers:
        return 'NEW_USERS';
      case StoryAudience.hasCard:
        return 'HAS_CARD';
      case StoryAudience.noCard:
        return 'NO_CARD';
      case StoryAudience.hasBankAccount:
        return 'HAS_BANK_ACCOUNT';
      case StoryAudience.noBankAccount:
        return 'NO_BANK_ACCOUNT';
      case StoryAudience.all:
        return 'ALL';
    }
  }
}

enum StoryStatus {
  draft,
  published,
  archived;

  static StoryStatus fromJson(String? val) {
    switch (val?.toUpperCase()) {
      case 'DRAFT':
        return StoryStatus.draft;
      case 'ARCHIVED':
        return StoryStatus.archived;
      case 'PUBLISHED':
      default:
        return StoryStatus.published;
    }
  }
}

class StoryPage {
  final String text;
  final String? imageRef;
  final String? actionLabel;
  final String? deepLink;

  const StoryPage({
    required this.text,
    this.imageRef,
    this.actionLabel,
    this.deepLink,
  });

  factory StoryPage.fromJson(Map<String, dynamic> json) {
    return StoryPage(
      text: json['text'] as String? ?? '',
      imageRef: json['imageRef'] as String?,
      actionLabel: json['actionLabel'] as String?,
      deepLink: json['deepLink'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'text': text,
        if (imageRef != null) 'imageRef': imageRef,
        if (actionLabel != null) 'actionLabel': actionLabel,
        if (deepLink != null) 'deepLink': deepLink,
      };
}

class Story {
  final String id;
  final String title;
  final String caption;
  final String imageOrGradient;
  final List<StoryPage> contentPages;
  final StoryAudience audience;
  final DateTime? startsAt;
  final DateTime? endsAt;
  final int priority;
  final StoryStatus status;
  final bool seen;
  final int actionClickCount;

  const Story({
    required this.id,
    required this.title,
    required this.caption,
    required this.imageOrGradient,
    required this.contentPages,
    this.audience = StoryAudience.all,
    this.startsAt,
    this.endsAt,
    this.priority = 100,
    this.status = StoryStatus.published,
    this.seen = false,
    this.actionClickCount = 0,
  });

  Story copyWith({
    String? id,
    String? title,
    String? caption,
    String? imageOrGradient,
    List<StoryPage>? contentPages,
    StoryAudience? audience,
    DateTime? startsAt,
    DateTime? endsAt,
    int? priority,
    StoryStatus? status,
    bool? seen,
    int? actionClickCount,
  }) {
    return Story(
      id: id ?? this.id,
      title: title ?? this.title,
      caption: caption ?? this.caption,
      imageOrGradient: imageOrGradient ?? this.imageOrGradient,
      contentPages: contentPages ?? this.contentPages,
      audience: audience ?? this.audience,
      startsAt: startsAt ?? this.startsAt,
      endsAt: endsAt ?? this.endsAt,
      priority: priority ?? this.priority,
      status: status ?? this.status,
      seen: seen ?? this.seen,
      actionClickCount: actionClickCount ?? this.actionClickCount,
    );
  }

  factory Story.fromJson(Map<String, dynamic> json) {
    final rawPages = json['contentPages'] as List<dynamic>? ?? [];
    return Story(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '',
      caption: json['caption'] as String? ?? '',
      imageOrGradient: json['imageOrGradient'] as String? ??
          'gradient:linear(135deg, #4F46E5, #3B82F6)',
      contentPages: rawPages
          .map((p) => StoryPage.fromJson(p as Map<String, dynamic>))
          .toList(),
      audience: StoryAudience.fromJson(json['audience'] as String?),
      startsAt: json['startsAt'] != null
          ? DateTime.tryParse(json['startsAt'] as String)
          : null,
      endsAt: json['endsAt'] != null
          ? DateTime.tryParse(json['endsAt'] as String)
          : null,
      priority: (json['priority'] as num?)?.toInt() ?? 100,
      status: StoryStatus.fromJson(json['status'] as String?),
      seen: json['seen'] as bool? ?? false,
      actionClickCount: (json['actionClickCount'] as num?)?.toInt() ?? 0,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'caption': caption,
        'imageOrGradient': imageOrGradient,
        'contentPages': contentPages.map((p) => p.toJson()).toList(),
        'audience': audience.toJson(),
        if (startsAt != null) 'startsAt': startsAt!.toIso8601String(),
        if (endsAt != null) 'endsAt': endsAt!.toIso8601String(),
        'priority': priority,
        'status': status.name.toUpperCase(),
        'seen': seen,
        'actionClickCount': actionClickCount,
      };
}
