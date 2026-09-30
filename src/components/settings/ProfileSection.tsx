import { useState, type FormEvent } from 'react';
import { Mail, User as UserIcon, Camera } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Button } from '../ui/Button';
import { Avatar } from '../ui/Avatar';
import { Alert } from '../ui/Alert';

export function ProfileSection() {
  const [name, setName] = useState('Rickael Brayan');
  const [email, setEmail] = useState('rickael@company.com');
  const [bio, setBio] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setIsSaving(true);
    setSuccess(false);
    await new Promise((r) => setTimeout(r, 600));
    setIsSaving(false);
    setSuccess(true);
    setTimeout(() => setSuccess(false), 3000);
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Profil</h2>
        <p className="text-sm text-muted-foreground">
          Gérez vos informations personnelles.
        </p>
      </div>

      {success && (
        <Alert variant="success">
          Vos informations ont été mises à jour.
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Photo de profil</CardTitle>
          <CardDescription>
            Cette photo apparaîtra sur votre profil et vos commentaires.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-4">
            <Avatar name={name} size="lg" />
            <div className="flex gap-2">
              <Button variant="outline" size="sm" leftIcon={<Camera className="h-4 w-4" />}>
                Changer
              </Button>
              <Button variant="ghost" size="sm">Supprimer</Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Informations personnelles</CardTitle>
          <CardDescription>
            Mettez à jour vos informations de compte.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input
              label="Nom complet"
              name="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              leftIcon={<UserIcon className="h-4 w-4" />}
            />
            <Input
              label="Email"
              name="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail className="h-4 w-4" />}
            />
            <Textarea
              label="Bio"
              name="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="Quelques mots sur vous..."
            />
            <div className="flex justify-end">
              <Button type="submit" isLoading={isSaving}>
                Enregistrer
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}