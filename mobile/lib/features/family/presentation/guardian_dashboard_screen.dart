import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/test_keys.dart';
import '../data/family_service.dart';
import '../domain/family_member_model.dart';

class GuardianDashboardScreen extends ConsumerStatefulWidget {
  const GuardianDashboardScreen({super.key});

  @override
  ConsumerState<GuardianDashboardScreen> createState() =>
      _GuardianDashboardScreenState();
}

class _GuardianDashboardScreenState
    extends ConsumerState<GuardianDashboardScreen> {
  @override
  Widget build(BuildContext context) {
    final members = ref.watch(familyNotifierProvider);
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      backgroundColor:
          isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Family Finance',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 16),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: const Color(0xFFEC4899).withOpacity(0.15),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: const Color(0xFFEC4899).withOpacity(0.4),
              ),
            ),
            child: const Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.shield_outlined, size: 14, color: Color(0xFFEC4899)),
                SizedBox(width: 4),
                Text(
                  'SIMULATED',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFFEC4899),
                    letterSpacing: 0.5,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      body: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildLegalComplianceBanner(context, isDark),
                  const SizedBox(height: 20),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Family Members (${members.length})',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                          color: isDark ? Colors.white : const Color(0xFF0F172A),
                        ),
                      ),
                      ElevatedButton.icon(
                        key: AppTestKeys.familyAddMemberBtn,
                        onPressed: () => _showAddMemberModal(context),
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('Add Child'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF0EA5E9),
                          foregroundColor: Colors.white,
                          elevation: 0,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 14,
                            vertical: 8,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                ],
              ),
            ),
          ),
          if (members.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.family_restroom,
                      size: 64,
                      color: isDark ? Colors.grey[700] : Colors.grey[400],
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'No family profiles added yet',
                      style: TextStyle(
                        fontSize: 16,
                        color: isDark ? Colors.grey[400] : Colors.grey[600],
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
                    final member = members[index];
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 16.0),
                      child: _buildMemberCard(context, member, isDark),
                    );
                  },
                  childCount: members.length,
                ),
              ),
            ),
          const SliverToBoxAdapter(child: SizedBox(height: 40)),
        ],
      ),
    );
  }

  Widget _buildLegalComplianceBanner(BuildContext context, bool isDark) {
    return Container(
      key: AppTestKeys.familySimulatedNotice,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E293B) : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(isDark ? 0.2 : 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: const Color(0xFFF59E0B).withOpacity(0.15),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.info_outline,
                  color: Color(0xFFF59E0B),
                  size: 20,
                ),
              ),
              const SizedBox(width: 10),
              const Expanded(
                child: Text(
                  'Guardian Sandbox & Data Privacy Notice',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFFF59E0B),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            'This is a SIMULATED sandbox for minor account management. Minors have NO independent phone numbers, NO registry discoverability, and cannot receive funds from strangers. Only verified adult guardians can fund balances.',
            style: TextStyle(
              fontSize: 12,
              height: 1.4,
              color: isDark ? Colors.grey[300] : const Color(0xFF475569),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Note: Real-world production deployment requires independent legal counsel regarding minors\' data privacy (COPPA, GDPR-K) and verifiable guardian consent.',
            style: TextStyle(
              fontSize: 11,
              fontStyle: FontStyle.italic,
              color: isDark ? Colors.grey[400] : const Color(0xFF64748B),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMemberCard(
      BuildContext context, FamilyMemberModel member, bool isDark) {
    final balanceText =
        'Rs. ${(member.balanceMinor / 100).toStringAsFixed(2)}';
    final dailyLimitText =
        'Rs. ${(member.dailyLimitMinor / 100).toStringAsFixed(2)}';
    final todaySpentText =
        'Rs. ${(member.todaySpentMinor / 100).toStringAsFixed(2)}';
    final spentRatio = member.dailyLimitMinor > 0
        ? (member.todaySpentMinor / member.dailyLimitMinor).clamp(0.0, 1.0)
        : 0.0;

    return Container(
      key: AppTestKeys.family(member.id),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E293B) : Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: member.isFrozen
              ? Colors.red.withOpacity(0.4)
              : (isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0)),
          width: member.isFrozen ? 1.5 : 1.0,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(isDark ? 0.3 : 0.05),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 24,
                backgroundColor:
                    member.isFrozen ? Colors.red.shade100 : const Color(0xFF0EA5E9),
                child: Text(
                  member.avatarInitials,
                  style: TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 16,
                    color: member.isFrozen ? Colors.red.shade900 : Colors.white,
                  ),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            member.firstName,
                            style: TextStyle(
                              fontSize: 17,
                              fontWeight: FontWeight.w700,
                              color: isDark ? Colors.white : const Color(0xFF0F172A),
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: isDark
                                ? const Color(0xFF334155)
                                : const Color(0xFFF1F5F9),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            _formatAgeBand(member.ageBand),
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                              color: isDark
                                  ? Colors.grey[300]
                                  : const Color(0xFF475569),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      member.isFrozen ? 'ACCOUNT FROZEN' : 'Active Account',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: member.isFrozen ? Colors.red : Colors.green,
                      ),
                    ),
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    balanceText,
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: isDark ? Colors.white : const Color(0xFF0F172A),
                    ),
                  ),
                  Text(
                    'Simulated Balance',
                    style: TextStyle(
                      fontSize: 11,
                      color: isDark ? Colors.grey[400] : Colors.grey[500],
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 16),
          // Daily limit progress bar
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Daily Spend: $todaySpentText / $dailyLimitText',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: isDark ? Colors.grey[300] : const Color(0xFF334155),
                    ),
                  ),
                  Text(
                    '${(spentRatio * 100).toInt()}%',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: spentRatio > 0.9 ? Colors.red : const Color(0xFF0EA5E9),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: spentRatio,
                  minHeight: 6,
                  backgroundColor:
                      isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                  valueColor: AlwaysStoppedAnimation<Color>(
                    spentRatio > 0.9 ? Colors.red : const Color(0xFF0EA5E9),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          // Allowed categories preview
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: member.allowedCategories.map((cat) {
              return Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isDark
                      ? const Color(0xFF334155)
                      : const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  cat,
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    color: isDark ? Colors.grey[300] : const Color(0xFF475569),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 16),
          const Divider(height: 1),
          const SizedBox(height: 12),
          // Actions Row
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  key: AppTestKeys.familyLimit,
                  onPressed: () => _showControlsModal(context, member),
                  icon: const Icon(Icons.tune, size: 16),
                  label: const Text('Limits & Rules'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor:
                        isDark ? Colors.white : const Color(0xFF0F172A),
                    side: BorderSide(
                      color: isDark
                          ? const Color(0xFF475569)
                          : const Color(0xFFCBD5E1),
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton.icon(
                  key: AppTestKeys.familyFundBtn,
                  onPressed: () => _showFundModal(context, member),
                  icon: const Icon(Icons.arrow_upward, size: 16),
                  label: const Text('Fund'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF10B981),
                    foregroundColor: Colors.white,
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Freeze toggle
              Row(
                children: [
                  Switch.adaptive(
                    key: AppTestKeys.familyFreezeToggle,
                    value: member.isFrozen,
                    activeColor: Colors.red,
                    onChanged: (val) {
                      ref
                          .read(familyNotifierProvider.notifier)
                          .toggleFreeze(member.id, val);
                    },
                  ),
                  Text(
                    member.isFrozen ? 'Frozen' : 'Unfrozen',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: member.isFrozen ? Colors.red : Colors.grey,
                    ),
                  ),
                ],
              ),
              Row(
                children: [
                  TextButton.icon(
                    key: AppTestKeys.familyChildViewBtn,
                    onPressed: () {
                      context.push('/family/child/${member.id}');
                    },
                    icon: const Icon(Icons.child_care, size: 16),
                    label: const Text('Child View'),
                    style: TextButton.styleFrom(
                      foregroundColor: const Color(0xFF0EA5E9),
                    ),
                  ),
                  IconButton(
                    key: AppTestKeys.familyDeleteBtn,
                    icon: const Icon(Icons.delete_outline,
                        color: Colors.red, size: 20),
                    tooltip: 'Delete Profile (Purge Data)',
                    onPressed: () => _confirmDelete(context, member),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _formatAgeBand(String band) {
    switch (band) {
      case 'UNDER_10':
        return '< 10 yrs';
      case 'AGE_10_TO_13':
        return '10-13 yrs';
      case 'AGE_14_TO_17':
        return '14-17 yrs';
      default:
        return band;
    }
  }

  void _showAddMemberModal(BuildContext context) {
    final nameController = TextEditingController();
    String selectedAgeBand = 'UNDER_10';

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            return Padding(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 20,
                bottom: MediaQuery.of(context).viewInsets.bottom + 20,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Add Child Profile',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'Data Minimisation: Provide first name only. No phone number or sensitive personal data is stored.',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    key: AppTestKeys.familyFirstNameInput,
                    controller: nameController,
                    decoration: const InputDecoration(
                      labelText: 'First Name',
                      hintText: 'e.g. Kasun',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 14),
                  DropdownButtonFormField<String>(
                    key: AppTestKeys.familyAgeBandInput,
                    value: selectedAgeBand,
                    decoration: const InputDecoration(
                      labelText: 'Age Band',
                      border: OutlineInputBorder(),
                    ),
                    items: const [
                      DropdownMenuItem(
                          value: 'UNDER_10', child: Text('Under 10 years')),
                      DropdownMenuItem(
                          value: 'AGE_10_TO_13',
                          child: Text('10 to 13 years')),
                      DropdownMenuItem(
                          value: 'AGE_14_TO_17',
                          child: Text('14 to 17 years')),
                    ],
                    onChanged: (val) {
                      if (val != null) {
                        setModalState(() => selectedAgeBand = val);
                      }
                    },
                  ),
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      key: AppTestKeys.familyCreateSubmitBtn,
                      onPressed: () {
                        if (nameController.text.trim().isEmpty) return;
                        ref.read(familyNotifierProvider.notifier).createMember(
                              firstName: nameController.text.trim(),
                              ageBand: selectedAgeBand,
                            );
                        Navigator.pop(ctx);
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF0EA5E9),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      child: const Text('Create Profile'),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  void _showControlsModal(BuildContext context, FamilyMemberModel member) {
    final dailyCtrl = TextEditingController(
      text: (member.dailyLimitMinor / 100).toStringAsFixed(0),
    );
    final monthlyCtrl = TextEditingController(
      text: (member.monthlyLimitMinor / 100).toStringAsFixed(0),
    );

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return Padding(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 20,
            bottom: MediaQuery.of(context).viewInsets.bottom + 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Spending Controls - ${member.firstName}',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 16),
              TextField(
                key: AppTestKeys.familyDailyLimitInput,
                controller: dailyCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Daily Spending Limit (LKR)',
                  border: OutlineInputBorder(),
                  prefixText: 'Rs. ',
                ),
              ),
              const SizedBox(height: 14),
              TextField(
                key: AppTestKeys.familyMonthlyLimitInput,
                controller: monthlyCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Monthly Spending Limit (LKR)',
                  border: OutlineInputBorder(),
                  prefixText: 'Rs. ',
                ),
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  key: AppTestKeys.familySaveLimitsBtn,
                  onPressed: () {
                    final daily = int.tryParse(dailyCtrl.text) ?? 1000;
                    final monthly = int.tryParse(monthlyCtrl.text) ?? 10000;
                    ref.read(familyNotifierProvider.notifier).updateControls(
                          member.id,
                          dailyLimitMinor: daily * 100,
                          monthlyLimitMinor: monthly * 100,
                        );
                    Navigator.pop(ctx);
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF0EA5E9),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  child: const Text('Save Controls'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _showFundModal(BuildContext context, FamilyMemberModel member) {
    final amountCtrl = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return Padding(
          padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 20,
            bottom: MediaQuery.of(context).viewInsets.bottom + 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Fund Allowance - ${member.firstName}',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 8),
              const Text(
                'Funds are credited from your guardian account into the simulated minor balance.',
                style: TextStyle(fontSize: 12, color: Colors.grey),
              ),
              const SizedBox(height: 16),
              TextField(
                key: AppTestKeys.familyFundAmountInput,
                controller: amountCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Amount (LKR)',
                  border: OutlineInputBorder(),
                  prefixText: 'Rs. ',
                ),
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  key: AppTestKeys.familyFundSubmitBtn,
                  onPressed: () {
                    final amount = int.tryParse(amountCtrl.text) ?? 0;
                    if (amount > 0) {
                      ref.read(familyNotifierProvider.notifier).fundMember(
                            member.id,
                            amount * 100,
                            sourceAccount: 'Guardian Primary Account',
                            note: 'Simulated pocket money',
                          );
                    }
                    Navigator.pop(ctx);
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF10B981),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  child: const Text('Confirm Funding'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _confirmDelete(BuildContext context, FamilyMemberModel member) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Delete ${member.firstName}\'s Profile?'),
        content: const Text(
          'Under data minimisation & retention rules, deleting this profile permanently removes all child records and transaction histories.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              ref
                  .read(familyNotifierProvider.notifier)
                  .deleteMember(member.id);
              Navigator.pop(ctx);
            },
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Delete Profile'),
          ),
        ],
      ),
    );
  }
}
