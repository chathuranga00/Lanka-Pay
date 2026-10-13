import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Whitelist validator for safe in-app story deep-links.
/// Prevents phishing, external navigation, open-redirects, and script injection.
class StoryDeepLinkValidator {
  StoryDeepLinkValidator._();

  static const List<String> allowedPaths = [
    '/accounts/add',
    '/accounts',
    '/settings/privacy',
    '/settings/security',
    '/settings',
    '/qr',
    '/history',
    '/rewards',
    '/bills',
    '/goals',
    '/send',
    '/explore',
    '/search',
    '/notifications',
  ];

  /// Checks if a deep-link is safe and belongs to the allowed route list.
  static bool isWhitelisted(String? link) {
    if (link == null || link.trim().isEmpty) return false;
    final trimmed = link.trim();

    // Reject raw HTML or script tags
    if (trimmed.contains('<') || trimmed.contains('>') || trimmed.toLowerCase().contains('javascript:')) {
      return false;
    }

    // Reject arbitrary web / external URLs
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://') || trimmed.startsWith('data:')) {
      return false;
    }

    String path = trimmed;
    if (path.startsWith('lankapay://')) {
      path = path.substring('lankapay://'.length);
      if (!path.startsWith('/')) {
        path = '/$path';
      }
    }

    // Must start with '/'
    if (!path.startsWith('/')) {
      return false;
    }

    // Split query params or fragment
    final uri = Uri.tryParse(path);
    final routePath = uri?.path ?? path;

    return allowedPaths.any((allowed) => routePath == allowed || routePath.startsWith('$allowed/'));
  }

  /// Extracts the canonical router path if whitelisted, otherwise returns null.
  static String? getNormalizedPath(String? link) {
    if (!isWhitelisted(link)) return null;
    var trimmed = link!.trim();
    if (trimmed.startsWith('lankapay://')) {
      trimmed = trimmed.substring('lankapay://'.length);
      if (!trimmed.startsWith('/')) {
        trimmed = '/$trimmed';
      }
    }
    return trimmed;
  }

  /// Safely navigates using GoRouter if whitelisted. Returns true if navigated.
  static bool navigate(BuildContext context, String? link) {
    final path = getNormalizedPath(link);
    if (path == null) {
      return false;
    }
    try {
      context.push(path);
      return true;
    } catch (_) {
      return false;
    }
  }
}
