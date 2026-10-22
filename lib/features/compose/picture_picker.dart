import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:image_picker_android/image_picker_android.dart';
import 'package:image_picker_platform_interface/image_picker_platform_interface.dart'
    show ImagePickerPlatform;

/// Picks pictures from the gallery, at most [limit]; overridden in tests.
final picturePickerProvider =
    Provider<Future<List<String>> Function(int limit)>((ref) {
      // Android 13+ (and 11–12 with Google Play services) then has the
      // system photo picker, which keeps to the limit, rather than
      // whatever answers "get content"; 16+ has it anyway.
      if (ImagePickerPlatform.instance case final ImagePickerAndroid android) {
        android.useAndroidPhotoPicker = true;
      }
      return (limit) async {
        final picker = ImagePicker();
        // Without full metadata iOS asks for no photo library access.
        final files = limit > 1
            ? await picker.pickMultiImage(
                limit: limit,
                requestFullMetadata: false,
              )
            : [
                ?await picker.pickImage(
                  source: ImageSource.gallery,
                  requestFullMetadata: false,
                ),
              ];
        return [for (final f in files) f.path];
      };
    });
