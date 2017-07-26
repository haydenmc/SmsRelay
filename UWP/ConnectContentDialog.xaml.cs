using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Devices.Bluetooth;
using Windows.Devices.Enumeration;
using Windows.Foundation;
using Windows.Foundation.Collections;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Controls.Primitives;
using Windows.UI.Xaml.Data;
using Windows.UI.Xaml.Input;
using Windows.UI.Xaml.Media;
using Windows.UI.Xaml.Navigation;

// The Content Dialog item template is documented at https://go.microsoft.com/fwlink/?LinkId=234238

namespace SmsRelay
{
    public sealed partial class ConnectContentDialog : ContentDialog
    {
        public class DeviceListItem : INotifyPropertyChanged
        {
            private string id { get; set; }
            public string Id
            {
                get
                {
                    return id;
                }
                set
                {
                    id = value;
                    OnPropertyChanged();
                }
            }
            public string DisplayId { get; set; }
            private string name;
            public string Name
            {
                get
                {
                    return name;
                }
                set
                {
                    name = value;
                    OnPropertyChanged();
                }
            }

            public event PropertyChangedEventHandler PropertyChanged;

            protected void OnPropertyChanged([CallerMemberName] string name = "")
            {
                if (PropertyChanged != null)
                {
                    PropertyChanged(this, new PropertyChangedEventArgs(name));
                }
            }
        }

        public ObservableCollection<DeviceListItem> Devices { get; set; }
            = new ObservableCollection<DeviceListItem>();

        public ConnectContentDialog()
        {
            this.InitializeComponent();
        }

        private void ScanForDevices()
        {
            // Query for extra properties you want returned
            string[] requestedProperties = { "System.Devices.Aep.DeviceAddress", "System.Devices.Aep.IsConnected" };

            DeviceWatcher deviceWatcher =
                DeviceInformation.CreateWatcher(
                    BluetoothLEDevice.GetDeviceSelectorFromPairingState(false),
                    requestedProperties,
                    DeviceInformationKind.AssociationEndpoint
                );

            // Register event handlers before starting the watcher.
            // Added, Updated and Removed are required to get all nearby devices
            deviceWatcher.Added += DeviceWatcher_Added;
            deviceWatcher.Updated += DeviceWatcher_Updated;
            deviceWatcher.Removed += DeviceWatcher_Removed;

            // EnumerationCompleted and Stopped are optional to implement.
            //deviceWatcher.EnumerationCompleted += DeviceWatcher_EnumerationCompleted;
            //deviceWatcher.Stopped += DeviceWatcher_Stopped;

            // Start the watcher.
            deviceWatcher.Start();
        }

        private async void DeviceWatcher_Removed(DeviceWatcher sender, DeviceInformationUpdate args)
        {
            //await Dispatcher.RunAsync(Windows.UI.Core.CoreDispatcherPriority.Normal, () =>
            //{
            //    var device = Devices.SingleOrDefault(d => d.Id == args.Id);
            //    if (device != null)
            //    {
            //        Devices.Remove(device);
            //    }
            //});
        }

        private async void DeviceWatcher_Updated(DeviceWatcher sender, DeviceInformationUpdate args)
        {
            await Dispatcher.RunAsync(Windows.UI.Core.CoreDispatcherPriority.Normal, () =>
            {
                var device = Devices.SingleOrDefault(d => d.Id == args.Id);
                if (device == null)
                {
                    device = new DeviceListItem()
                    {
                        Id = args.Id,
                        DisplayId = args.Id.Substring(args.Id.LastIndexOf("-") + 1)
                    };
                    Devices.Add(device);
                }
                System.Diagnostics.Debug.WriteLine($"New properties for {args.Id}:");
                foreach (var prop in args.Properties)
                {
                    System.Diagnostics.Debug.WriteLine($"\t{prop.Key} : {prop.Value}");
                }
                if (args.Properties.ContainsKey("System.ItemNameDisplay"))
                {
                    var updatedName = args.Properties["System.ItemNameDisplay"] as string;
                    if (updatedName != null && updatedName.Length > 0)
                    {
                        device.Name = updatedName;
                    }
                }
                if (args.Properties.ContainsKey("System.Devices.Aep.DeviceAddress"))
                {
                    var updatedId = args.Properties["System.Devices.Aep.DeviceAddress"] as string;
                    if (updatedId != null && updatedId.Length > 0)
                    {
                        device.Id = updatedId;
                    }
                }
            });
        }

        private async void DeviceWatcher_Added(DeviceWatcher sender, DeviceInformation args)
        {
            await Dispatcher.RunAsync(Windows.UI.Core.CoreDispatcherPriority.Normal, () =>
            {
                var existingDevice = Devices.SingleOrDefault(d => d.Id == args.Id);
                if (existingDevice != null)
                {
                    if (args.Name.Length > 0)
                    {
                        existingDevice.Name = args.Name;
                    }
                }
                else
                {
                    var deviceListItem = new DeviceListItem()
                    {
                        Id = args.Id,
                        DisplayId = args.Id.Substring(args.Id.LastIndexOf("-") + 1),
                        Name = args.Name.Length > 0 ? args.Name : "Bluetooth LE Device"
                    };
                    Devices.Add(deviceListItem);
                }
            });
        }

        private void ContentDialog_PrimaryButtonClick(ContentDialog sender, ContentDialogButtonClickEventArgs args)
        {
        }

        private void ContentDialog_SecondaryButtonClick(ContentDialog sender, ContentDialogButtonClickEventArgs args)
        {
        }

        private void OnOpened(ContentDialog sender, ContentDialogOpenedEventArgs args)
        {
            ScanForDevices();
        }
    }
}
