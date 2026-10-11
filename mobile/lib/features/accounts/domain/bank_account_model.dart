class BankAccountModel {
  final int id;
  final String institutionCode;
  final String accountName;
  final String maskedNumber;
  final String? label;
  final String status;
  final bool isDefault;
  final bool discoverable;
  final bool receivingEnabled;
  final bool simulated;
  final String? linkedAt;

  const BankAccountModel({
    required this.id,
    required this.institutionCode,
    required this.accountName,
    required this.maskedNumber,
    this.label,
    required this.status,
    required this.isDefault,
    required this.discoverable,
    required this.receivingEnabled,
    this.simulated = true,
    this.linkedAt,
  });

  factory BankAccountModel.fromJson(Map<String, dynamic> json) {
    return BankAccountModel(
      id: json['id'] is int ? json['id'] as int : int.parse(json['id'].toString()),
      institutionCode: json['institutionCode'] as String? ?? '',
      accountName: json['accountName'] as String? ?? '',
      maskedNumber: json['maskedNumber'] as String? ?? '',
      label: json['label'] as String?,
      status: json['status'] as String? ?? 'ACTIVE',
      isDefault: json['isDefault'] as bool? ?? false,
      discoverable: json['discoverable'] as bool? ?? true,
      receivingEnabled: json['receivingEnabled'] as bool? ?? true,
      simulated: json['simulated'] as bool? ?? true,
      linkedAt: json['linkedAt'] as String?,
    );
  }

  BankAccountModel copyWith({
    String? label,
    bool? isDefault,
    bool? discoverable,
    bool? receivingEnabled,
    String? status,
  }) {
    return BankAccountModel(
      id: id,
      institutionCode: institutionCode,
      accountName: accountName,
      maskedNumber: maskedNumber,
      label: label ?? this.label,
      status: status ?? this.status,
      isDefault: isDefault ?? this.isDefault,
      discoverable: discoverable ?? this.discoverable,
      receivingEnabled: receivingEnabled ?? this.receivingEnabled,
      simulated: simulated,
      linkedAt: linkedAt,
    );
  }
}

class BankInstitutionModel {
  final String institutionCode;
  final String displayName;
  final bool simulated;

  const BankInstitutionModel({
    required this.institutionCode,
    required this.displayName,
    this.simulated = true,
  });

  factory BankInstitutionModel.fromJson(Map<String, dynamic> json) {
    return BankInstitutionModel(
      institutionCode: json['institutionCode'] as String? ?? '',
      displayName: json['displayName'] as String? ?? '',
      simulated: json['simulated'] as bool? ?? true,
    );
  }
}
