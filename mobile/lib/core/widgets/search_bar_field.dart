import 'package:flutter/material.dart';
import '../theme/app_tokens.dart';

/// Clean rounded search input field matching the target design language.
/// Radius ~18, subtle background fill ~#EFEFF1, WCAG AA compliant contrast.
class SearchBarField extends StatelessWidget {
  final String hintText;
  final TextEditingController? controller;
  final ValueChanged<String>? onChanged;
  final VoidCallback? onTap;
  final VoidCallback? onClear;
  final ValueChanged<String>? onSubmitted;
  final bool readOnly;
  final bool autofocus;
  final FocusNode? focusNode;
  final Widget? trailing;

  const SearchBarField({
    super.key = const Key('search_bar_field'),
    this.hintText = 'Search',
    this.controller,
    this.onChanged,
    this.onSubmitted,
    this.onTap,
    this.onClear,
    this.readOnly = false,
    this.autofocus = false,
    this.focusNode,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bgColor = isDark ? AppTokens.searchFieldBgDark : AppTokens.searchFieldBgLight;
    final hintColor = isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight;
    final textColor = isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight;

    return Semantics(
      textField: true,
      label: hintText,
      child: Container(
        height: 44,
        decoration: BoxDecoration(
          color: bgColor,
          borderRadius: AppTokens.borderRadiusSearch,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 14),
        child: Row(
          children: [
            Icon(
              Icons.search_rounded,
              size: 20,
              color: hintColor,
              key: const Key('search_bar_field_icon'),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: TextField(
                key: const Key('search_bar_field_input'),
                controller: controller,
                focusNode: focusNode,
                readOnly: readOnly,
                autofocus: autofocus,
                onTap: onTap,
                onChanged: onChanged,
                onSubmitted: onSubmitted,
                style: TextStyle(
                  fontFamily: AppTokens.fontFamily,
                  fontSize: 15,
                  fontWeight: FontWeight.w500,
                  color: textColor,
                ),
                decoration: InputDecoration(
                  hintText: hintText,
                  hintStyle: TextStyle(
                    fontFamily: AppTokens.fontFamily,
                    fontSize: 15,
                    fontWeight: FontWeight.w400,
                    color: hintColor,
                  ),
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  isDense: true,
                  contentPadding: EdgeInsets.zero,
                  filled: false,
                ),
              ),
            ),
            if (trailing != null) trailing!,
          ],
        ),
      ),
    );
  }
}
