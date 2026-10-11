import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../../core/theme/app_theme.dart';
import '../data/qr_service.dart';
import '../domain/qr_models.dart';

class QrScannerScreen extends ConsumerStatefulWidget {
  final Function(ResolvedQrModel resolved)? onResolved;

  const QrScannerScreen({super.key, this.onResolved});

  @override
  ConsumerState<QrScannerScreen> createState() => _QrScannerScreenState();
}

class _QrScannerScreenState extends ConsumerState<QrScannerScreen> {
  final MobileScannerController _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.normal,
    facing: CameraFacing.back,
  );
  bool _isProcessing = false;
  String? _errorMessage;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _handleBarcode(String rawValue) async {
    if (_isProcessing) return;
    setState(() {
      _isProcessing = true;
      _errorMessage = null;
    });

    try {
      final resolved = await ref.read(qrServiceProvider).resolveReference(rawValue);

      if (resolved.isExpired) {
        if (mounted) {
          setState(() {
            _isProcessing = false;
            _errorMessage = 'This QR code has expired. Please ask the recipient for a new code.';
          });
        }
        return;
      }

      if (resolved.isUsed) {
        if (mounted) {
          setState(() {
            _isProcessing = false;
            _errorMessage = 'This one-time QR code has already been used.';
          });
        }
        return;
      }

      if (mounted) {
        if (widget.onResolved != null) {
          widget.onResolved!(resolved);
        } else {
          Navigator.of(context).pop(resolved);
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isProcessing = false;
          _errorMessage = 'Unable to resolve QR reference. Ensure it is a valid LankaPay QR code.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Scan LankaPay QR'),
        actions: [
          IconButton(
            icon: const Icon(Icons.flash_on_rounded),
            onPressed: () => _controller.toggleTorch(),
          ),
          IconButton(
            icon: const Icon(Icons.flip_camera_ios_rounded),
            onPressed: () => _controller.switchCamera(),
          ),
        ],
      ),
      body: Stack(
        children: [
          MobileScanner(
            controller: _controller,
            onDetect: (capture) {
              final barcodes = capture.barcodes;
              for (final barcode in barcodes) {
                final raw = barcode.rawValue;
                if (raw != null && raw.isNotEmpty) {
                  _handleBarcode(raw);
                  break;
                }
              }
            },
          ),
          // Scanner Overlay Frame
          Center(
            child: Container(
              width: 260,
              height: 260,
              decoration: BoxDecoration(
                border: Border.all(color: AppColors.accentGold, width: 3),
                borderRadius: BorderRadius.circular(20),
              ),
            ),
          ),
          // Feedback banner at bottom
          Positioned(
            bottom: 40,
            left: 24,
            right: 24,
            child: Column(
              children: [
                if (_errorMessage != null)
                  Container(
                    margin: const EdgeInsets.only(bottom: 16),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: AppColors.error,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      _errorMessage!,
                      style: const TextStyle(color: Colors.white, fontSize: 13),
                      textAlign: TextAlign.center,
                    ),
                  ),
                if (_isProcessing)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                    decoration: BoxDecoration(
                      color: Colors.black87,
                      borderRadius: BorderRadius.circular(30),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                        ),
                        SizedBox(width: 12),
                        Text(
                          'Resolving reference...',
                          style: TextStyle(color: Colors.white, fontSize: 14),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
